package com.api.generator.api.service;

import com.api.generator.account.ApiPreview;
import com.api.generator.account.GeneratedApi;
import com.api.generator.account.PreviewStatus;
import com.api.generator.config.GenerationJobProperties;
import com.api.generator.config.GeneratorProperties;
import com.api.generator.reader.H2SchemaReader;
import com.api.generator.schema.DatabaseType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPreviewRuntimeServiceSmokeTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @TempDir
    Path tempDir;

    @Test
    @EnabledIfSystemProperty(
            named = "runDockerSmokeTests",
            matches = "true",
            disabledReason = "This test starts Docker and builds a real image. It is disabled by default to keep standard CI stable. Run with: mvn test -DrunDockerSmokeTests=true"
    )
    void buildsAndRunsPreviewContainerFromGeneratedZip() throws Exception {
        LocalPreviewRuntimeService runtimeService = new LocalPreviewRuntimeService(jobProperties());
        Assumptions.assumeTrue(previewHostReady(runtimeService), "Preview host prerequisites are not available");

        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("preview-db").toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        executeSqlResource(dbUrl, "schemas/complex-creation-schema.sql");

        installRuntimeArtifacts();

        GenerationJobService generationJobService = new GenerationJobService(jobProperties(), new H2SchemaReader());
        String jobId = generationJobService.startGeneration(generatorProperties(dbUrl), false, false, false, null);
        JobInfo job = generationJobService.getJob(jobId).orElseThrow();
        assertEquals(JobStatus.SUCCEEDED, job.status(), () -> String.join("\n", generationJobService.getLogs(jobId, 200)));
        assertTrue(Files.exists(job.zipPath()));

        GeneratedApi generatedApi = new GeneratedApi();
        generatedApi.setName("PreviewSmoke");
        generatedApi.setFilePath(job.zipPath().toString());
        generatedApi.setStatus(com.api.generator.account.GenerationStatus.DONE);

        PreviewRuntimeService.PreviewLaunchConfig config = new PreviewRuntimeService.PreviewLaunchConfig(
                "H2",
                dbUrl,
                "sa",
                "",
                "public",
                "admin",
                "GeneratedAdminPassword123!",
                "H2cA8FXRjnAVgCBcVqt4FZ7FqHKx6VYjzu2ZK9YtFAe",
                "generated-api-preview-smoke",
                3600
        );

        PreviewRuntimeService.StartResult result = runtimeService.start(generatedApi, config);
        ApiPreview preview = new ApiPreview();
        preview.setStatus(PreviewStatus.RUNNING);
        preview.setContainerId(result.containerId());
        preview.setImageTag(result.imageTag());
        preview.setWorkspaceDir(result.workspaceDir());
        preview.setHostPort(result.hostPort());
        preview.setBaseUrl(result.baseUrl());
        preview.setCreatedAt(java.time.Instant.now());
        try {
            JsonNode openApi = waitForOpenApi(result.baseUrl());
            assertTrue(openApi.at("/paths/~1api~1organizations").has("get"));
            assertTrue(openApi.at("/paths/~1api~1tickets").has("post"));
            assertTrue(resolvePublishedPreviewAddress(result.containerId(), result.hostPort()).startsWith("127.0.0.1:"));
        } finally {
            runtimeService.stop(preview);
        }
    }

    private GenerationJobProperties jobProperties() {
        GenerationJobProperties props = new GenerationJobProperties();
        props.setTemplatePath(repoRoot().resolve("api-generator-template").toString());
        props.setTempDirectoryPrefix("generated-api-preview-smoke-");
        props.setCommandTimeoutSeconds(300);
        props.setDockerDeploymentEnabled(true);
        props.setOutputFolderName("generated-api");
        props.setZipFileName("generated-api.zip");
        return props;
    }

    private GeneratorProperties generatorProperties(String dbUrl) {
        GeneratorProperties props = new GeneratorProperties();
        props.setAppName("PreviewSmokeApi");
        props.setBasePackage("com.generated.preview");
        props.getDb().setType(DatabaseType.H2);
        props.getDb().setUrl(dbUrl);
        props.getDb().setUsername("sa");
        props.getDb().setPassword("");
        props.getDb().setSchema("public");
        props.getSecurity().setEnabled(true);
        props.getSecurity().setBootstrapUsername("admin");
        props.getSecurity().setBootstrapPassword("GeneratedAdminPassword123!");
        props.getSecurity().setJwtSecret("H2cA8FXRjnAVgCBcVqt4FZ7FqHKx6VYjzu2ZK9YtFAe");
        props.getSecurity().setJwtIssuer("generated-api-preview-smoke");
        props.getSecurity().setJwtExpirationSeconds(3600);
        props.getFeatures().setGenerateDocker(true);
        return props;
    }

    private void executeSqlResource(String dbUrl, String resource) throws Exception {
        String sql;
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test SQL resource: " + resource);
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var connection = DriverManager.getConnection(dbUrl, "sa", "");
             var statement = connection.createStatement()) {
            for (String rawStatement : sql.split(";")) {
                String ddl = rawStatement.trim();
                if (!ddl.isBlank()) {
                    statement.execute(ddl);
                }
            }
        }
    }

    private void installRuntimeArtifacts() throws Exception {
        runCommand(repoRoot(), mavenCommand("-q",
                "-pl", "api-generator-core,api-generator-runtime",
                "-am", "install", "-DskipTests"));
    }

    private JsonNode waitForOpenApi(String baseUrl) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(URI.create(baseUrl + "/v3/api-docs")).GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                if (response.statusCode() == 200) {
                    return JSON.readTree(response.body());
                }
                last = new IllegalStateException("Unexpected /v3/api-docs status " + response.statusCode());
            } catch (Exception e) {
                last = e;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(750));
        }
        throw new IllegalStateException("Preview container did not expose OpenAPI in time", last);
    }

    private boolean previewHostReady(LocalPreviewRuntimeService runtimeService) {
        return runtimeService.diagnoseHost().checks().stream().allMatch(PreviewRuntimeService.HostCheck::ok);
    }

    private String resolvePublishedPreviewAddress(String containerId, int hostPort) throws Exception {
        Process process = new ProcessBuilder("docker", "port", containerId, "8080/tcp")
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished || process.exitValue() != 0) {
            throw new IllegalStateException("Unable to resolve preview port mapping for " + containerId);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertTrue(output.endsWith(":" + hostPort), () -> "Unexpected preview port mapping: " + output);
        return output;
    }

    private void runCommand(Path workingDir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        Path log = tempDir.resolve("command-" + Math.abs(String.join("-", command).hashCode()) + ".log");
        pb.redirectOutput(log.toFile());
        Process process = pb.start();
        boolean finished = process.waitFor(300, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Command failed (" + process.exitValue() + "): "
                    + String.join(" ", command) + "\n" + Files.readString(log));
        }
    }

    private Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("api-generator-template"))
                    && Files.exists(current.resolve("api-generator-runtime"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root from " + Path.of("").toAbsolutePath());
    }

    private Path mavenWrapper() {
        String wrapper = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "mvnw.cmd" : "mvnw";
        return repoRoot().resolve(wrapper);
    }

    private String[] mavenCommand(String... args) {
        Path wrapper = mavenWrapper();
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        List<String> command = new ArrayList<>();
        if (!windows && !Files.isExecutable(wrapper)) {
            command.add("bash");
        }
        command.add(wrapper.toString());
        command.addAll(List.of(args));
        return command.toArray(String[]::new);
    }
}
