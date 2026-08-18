package com.api.generator.api.service;

import com.api.generator.account.ApiPreview;
import com.api.generator.account.GeneratedApi;
import com.api.generator.config.GenerationJobProperties;
import com.api.generator.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class LocalPreviewRuntimeService implements PreviewRuntimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalPreviewRuntimeService.class);
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final GenerationJobProperties jobProperties;

    public LocalPreviewRuntimeService(GenerationJobProperties jobProperties) {
        this.jobProperties = jobProperties;
    }

    @Override
    public StartResult start(GeneratedApi generatedApi, PreviewLaunchConfig config) throws Exception {
        validateHostPrerequisites();
        if (generatedApi.getFilePath() == null || generatedApi.getFilePath().isBlank()) {
            throw new IllegalStateException("Generated ZIP is not available");
        }
        Path zip = Path.of(generatedApi.getFilePath());
        if (!Files.exists(zip)) {
            throw new IllegalStateException("Generated ZIP is not available");
        }

        Path workspace = Files.createTempDirectory(jobProperties.getTempDirectoryPrefix() + "preview-");
        FileUtils.unzip(zip, workspace);

        exec(workspace, buildMavenCommand(workspace, "clean", "verify"));

        String previewKey = resolvePreviewKey(generatedApi);
        String imageTag = "apigen-preview-" + previewKey;
        execWithRetry(workspace, jobProperties.getContainerRuntime().buildImageCmd(imageTag, "."), 2);

        int hostPort = findFreePort();
        String containerName = "apigen-preview-" + previewKey;
        List<String> env = buildEnv(config);
        String containerId = execForSingleLine(workspace,
                jobProperties.getContainerRuntime().runContainerCmd(
                        containerName,
                        imageTag,
                        jobProperties.getPreviewBindHost(),
                        hostPort,
                        env
                ));

        return new StartResult(
                containerId,
                imageTag,
                workspace.toString(),
                hostPort,
                jobProperties.buildApiBaseUrl(hostPort)
        );
    }

    @Override
    public void stop(ApiPreview preview) throws Exception {
        var runtime = jobProperties.getContainerRuntime();
        if (preview.getContainerId() != null && !preview.getContainerId().isBlank()) {
            exec(Path.of("").toAbsolutePath(), runtime.stopContainerCmd(preview.getContainerId()));
            exec(Path.of("").toAbsolutePath(), runtime.rmContainerCmd(preview.getContainerId()));
        }
        if (preview.getImageTag() != null && !preview.getImageTag().isBlank()) {
            try {
                exec(Path.of("").toAbsolutePath(), runtime.removeImageCmd(preview.getImageTag()));
            } catch (Exception ignored) {
                // Best effort cleanup.
            }
        }
        if (preview.getWorkspaceDir() != null && !preview.getWorkspaceDir().isBlank()) {
            try {
                FileUtils.deleteDirectoryIfExists(Path.of(preview.getWorkspaceDir()));
            } catch (Exception ignored) {
                // Best effort cleanup.
            }
        }
    }

    @Override
    public List<String> logs(ApiPreview preview, int tail) throws Exception {
        if (preview.getContainerId() == null || preview.getContainerId().isBlank()) {
            return List.of();
        }
        String output = execForSingleLine(Path.of("").toAbsolutePath(),
                jobProperties.getContainerRuntime().logsCmd(preview.getContainerId(), tail));
        if (output.isBlank()) {
            return List.of();
        }
        return Arrays.stream(output.split("\\R"))
                .filter(line -> !line.isBlank())
                .toList();
    }

    @Override
    public HostDiagnostics diagnoseHost() {
        String runtimeBinary = jobProperties.getContainerRuntime().binary();
        List<HostCheck> checks = List.of(
                new HostCheck(
                        "containerRuntimeBinary",
                        commandAvailable(runtimeBinary, "--version"),
                        "Binary checked with `" + runtimeBinary + " --version`"
                ),
                new HostCheck(
                        "containerRuntimeReachable",
                        commandAvailable(runtimeBinary, "info"),
                        "Runtime checked with `" + runtimeBinary + " info`"
                ),
                new HostCheck(
                        "mavenCommandAvailable",
                        commandAvailable(buildMavenCommand(Path.of("").toAbsolutePath(), "--version").toArray(String[]::new)),
                        "Build command checked with Maven `--version`"
                )
        );
        return new HostDiagnostics(runtimeBinary, checks);
    }

    private List<String> buildEnv(PreviewLaunchConfig config) {
        List<String> env = new ArrayList<>();
        env.add("PORT=8080");
        env.add("DB_URL=" + toDockerJdbcUrl(config.jdbcUrl()));
        env.add("DB_USERNAME=" + nullToEmpty(config.jdbcUsername()));
        env.add("DB_PASSWORD=" + nullToEmpty(config.jdbcPassword()));
        env.add("DB_TYPE=" + nullToEmpty(config.databaseType()));
        env.add("DB_SCHEMA=" + (config.schema() == null || config.schema().isBlank() ? "public" : config.schema()));
        env.add("SECURITY_ENABLED=true");
        env.add("BOOTSTRAP_USER=" + nullToEmpty(config.bootstrapUsername()));
        env.add("BOOTSTRAP_PASSWORD_HASH=" + bcrypt(config.bootstrapPassword()));
        env.add("JWT_SECRET=" + nullToEmpty(config.jwtSecret()));
        env.add("JWT_ISSUER=" + nullToEmpty(config.jwtIssuer()));
        env.add("JWT_EXP_SECONDS=" + config.jwtExpirationSeconds());
        return env;
    }

    private List<String> buildMavenCommand(Path projectDir, String... args) {
        List<String> command = resolveBuildCommand(projectDir);
        command.addAll(List.of(args));
        return command;
    }

    private List<String> resolveBuildCommand(Path projectDir) {
        String wrapper = isWindows() ? "mvnw.cmd" : "mvnw";
        Path wrapperPath = projectDir.resolve(wrapper);
        if (Files.exists(wrapperPath)) {
            return wrapShellCommand(wrapperPath, "./" + wrapper);
        }
        String mavenHome = System.getProperty("maven.home");
        if (mavenHome != null && !mavenHome.isBlank()) {
            Path mavenBinary = Path.of(mavenHome, "bin", isWindows() ? "mvn.cmd" : "mvn");
            if (Files.exists(mavenBinary)) {
                return new ArrayList<>(List.of(mavenBinary.toString()));
            }
        }
        Path managerWrapper = locateManagerWrapper(wrapper);
        if (managerWrapper != null) {
            return wrapShellCommand(managerWrapper, managerWrapper.toString());
        }
        return new ArrayList<>(List.of("mvn"));
    }

    private void validateHostPrerequisites() {
        String runtimeBinary = jobProperties.getContainerRuntime().binary();
        if (!commandAvailable(runtimeBinary, "--version")) {
            throw new IllegalStateException("Container runtime binary is not available: " + runtimeBinary);
        }
        if (!commandAvailable(runtimeBinary, "info")) {
            throw new IllegalStateException("Container runtime is not reachable: " + runtimeBinary);
        }
        List<String> buildCommand = buildMavenCommand(Path.of("").toAbsolutePath(), "--version");
        if (!commandAvailable(buildCommand.toArray(String[]::new))) {
            throw new IllegalStateException("Maven build command is not available for preview runtime");
        }
    }

    private boolean commandAvailable(String... command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            consume(process);
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path locateManagerWrapper(String wrapperName) {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path wrapper = current.resolve(wrapperName);
            if (Files.exists(wrapper)) {
                return wrapper;
            }
            current = current.getParent();
        }
        return null;
    }

    private List<String> wrapShellCommand(Path commandPath, String fallbackCommand) {
        if (!isWindows() && !Files.isExecutable(commandPath)) {
            return new ArrayList<>(List.of("bash", fallbackCommand));
        }
        return new ArrayList<>(List.of(fallbackCommand));
    }

    private void exec(Path cwd, List<String> command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(cwd.toFile());
        processBuilder.redirectErrorStream(true);
        Path outputLog = Files.createTempFile("preview-command-", ".log");
        processBuilder.redirectOutput(outputLog.toFile());
        Process process = processBuilder.start();
        try {
            waitFor(command, process, outputLog);
        } finally {
            Files.deleteIfExists(outputLog);
        }
    }

    private void execWithRetry(Path cwd, List<String> command, int attempts) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                exec(cwd, command);
                return;
            } catch (Exception e) {
                last = e;
                if (attempt < attempts) {
                    LOGGER.warn("Preview runtime command failed on attempt {}/{}: {}", attempt, attempts, safeCommandLabel(command));
                }
            }
        }
        throw last;
    }

    private String execForSingleLine(Path cwd, List<String> command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(cwd.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
        }
        waitFor(command, process, null);
        return output.trim();
    }

    private void consume(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            reader.transferTo(Writer.nullWriter());
        }
    }

    private void waitFor(List<String> command, Process process, Path outputLog) throws Exception {
        boolean finished = process.waitFor(jobProperties.getCommandTimeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            if (outputLog != null && Files.exists(outputLog)) {
                String output = Files.readString(outputLog, StandardCharsets.UTF_8).trim();
                if (!output.isBlank()) {
                    LOGGER.warn("Preview runtime command failed: {}\n{}", safeCommandLabel(command), output);
                }
            }
            throw new IllegalStateException("Command failed (" + process.exitValue() + "): " + String.join(" ", command));
        }
    }

    private String safeCommandLabel(List<String> command) {
        if (command.isEmpty()) {
            return "unknown command";
        }
        if (command.size() >= 2 && ("docker".equals(command.get(0)) || "podman".equals(command.get(0)))) {
            return command.get(0) + " " + command.get(1);
        }
        if (command.stream().anyMatch(part -> part.endsWith("mvn") || part.endsWith("mvnw") || part.endsWith("mvnw.cmd"))) {
            return "maven build";
        }
        return command.get(0);
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return jobProperties.getDockerFallbackPort() + (int) (Instant.now().toEpochMilli() % 1000);
        }
    }

    private String bcrypt(String raw) {
        String value = nullToEmpty(raw);
        if (value.startsWith("$2")) {
            return value;
        }
        return PASSWORD_ENCODER.encode(value);
    }

    private String toDockerJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "";
        }
        return jdbcUrl.replace("localhost", "host.docker.internal")
                .replace("127.0.0.1", "host.docker.internal");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private String resolvePreviewKey(GeneratedApi generatedApi) {
        UUID id = generatedApi.getId();
        if (id != null) {
            return id.toString().toLowerCase(Locale.ROOT);
        }
        String name = generatedApi.getName();
        if (name != null && !name.isBlank()) {
            return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        }
        return UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
    }
}
