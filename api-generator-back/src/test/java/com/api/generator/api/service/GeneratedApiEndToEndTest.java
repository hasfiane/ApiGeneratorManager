package com.api.generator.api.service;

import com.api.generator.config.GenerationJobProperties;
import com.api.generator.config.GeneratorProperties;
import com.api.generator.reader.H2SchemaReader;
import com.api.generator.schema.DatabaseType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.net.ServerSocket;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedApiEndToEndTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @TempDir
    Path tempDir;

    @Test
    void generatesBuildsStartsAndExercisesApiFromComplexSchema() throws Exception {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("complex-db").toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        executeSqlResource(dbUrl, "schemas/complex-creation-schema.sql");

        GenerationJobService service = new GenerationJobService(jobProperties(), new H2SchemaReader());
        String jobId = service.startGeneration(generatorProperties(dbUrl), false, false, false, null);
        JobInfo job = service.getJob(jobId).orElseThrow();

        assertEquals(JobStatus.SUCCEEDED, job.status(), () -> String.join("\n", service.getLogs(jobId, 200)));
        assertTrue(Files.exists(job.zipPath()));
        assertTrue(Files.exists(job.outputDir().resolve("src/main/resources/schema.json")));
        installRuntimeArtifacts();
        packageGeneratedProject(job.outputDir());

        int port = freePort();
        Process api = startGeneratedApi(job.outputDir(), dbUrl, port);
        try {
            String baseUrl = "http://localhost:" + port;
            JsonNode openApi = waitForOpenApi(baseUrl);
            assertTrue(openApi.at("/paths/~1api~1organizations").has("get"));
            assertTrue(openApi.at("/paths/~1api~1organizations").has("post"));
            assertTrue(openApi.at("/paths/~1api~1users").has("post"));
            assertTrue(openApi.at("/paths/~1api~1projects").has("post"));
            assertTrue(openApi.at("/paths/~1api~1tickets").has("post"));
            assertTrue(openApi.at("/paths/~1api~1ticket_labels").has("post"));
            assertTrue(openApi.at("/paths/~1api~1ticket_labels~1{id}").has("get"));
            assertTrue(openApi.at("/paths/~1api~1deployment_events").has("post"));
            assertTrue(openApi.at("/components/schemas/Organizations/properties/billing_email").isObject());
            assertTrue(openApi.at("/components/schemas/Tickets/properties/estimate_points").isObject());

            int anonymousStatus = get(baseUrl + "/api/organizations", null).statusCode();
            assertTrue(anonymousStatus == 401 || anonymousStatus == 403);
            String token = login(baseUrl);

            HttpResponse<String> invalidOrganization = postJsonRaw(baseUrl + "/api/organizations", token, Map.of(
                    "slug", "acme",
                    "name", "ACME",
                    "billing_email", "billing@example.test",
                    "unknown_column", "should be rejected"
            ));
            assertEquals(400, invalidOrganization.statusCode(), invalidOrganization.body());
            assertTrue(invalidOrganization.body().contains("unknown_column"));

            Map<String, Object> organization = postJson(baseUrl + "/api/organizations", token, Map.of(
                    "slug", "acme",
                    "name", "ACME Inc",
                    "billing_email", "billing@acme.example",
                    "plan_code", "PRO",
                    "seat_limit", 20
            ));
            Object organizationId = requireValue(organization, "id");

            HttpResponse<String> invalidUser = postJsonRaw(baseUrl + "/api/users", token, Map.of(
                    "organization_id", organizationId,
                    "email", "owner@acme.example",
                    "display_name", "x".repeat(141)
            ));
            assertEquals(400, invalidUser.statusCode(), invalidUser.body());
            assertTrue(invalidUser.body().contains("display_name"));

            Map<String, Object> owner = postJson(baseUrl + "/api/users", token, Map.of(
                    "organization_id", organizationId,
                    "email", "owner@acme.example",
                    "display_name", "Owner User",
                    "role_code", "OWNER",
                    "active", true
            ));
            Object ownerId = requireValue(owner, "id");

            Map<String, Object> project = postJson(baseUrl + "/api/projects", token, Map.of(
                    "organization_id", organizationId,
                    "owner_user_id", ownerId,
                    "code", "core-api",
                    "name", "Core API",
                    "description", "Main integration test project"
            ));
            Object projectId = requireValue(project, "id");

            Map<String, Object> environment = postJson(baseUrl + "/api/environments", token, Map.of(
                    "project_id", projectId,
                    "name", "preprod",
                    "base_url", "https://preprod.acme.example",
                    "sort_order", 1
            ));
            Object environmentId = requireValue(environment, "id");

            Map<String, Object> apiKey = postJson(baseUrl + "/api/api_keys", token, Map.of(
                    "environment_id", environmentId,
                    "key_name", "smoke-test",
                    "key_hash", "hash-value-for-smoke-test",
                    "scopes", "read:tickets write:tickets"
            ));
            Object apiKeyId = requireValue(apiKey, "id");

            HttpResponse<String> invalidTicket = postJsonRaw(baseUrl + "/api/tickets", token, Map.of(
                    "public_ref", "TCK-001",
                    "title", "x".repeat(221),
                    "priority", "HIGH",
                    "status", "OPEN",
                    "estimate_points", "not-a-decimal"
            ));
            assertEquals(400, invalidTicket.statusCode(), invalidTicket.body());
            assertTrue(invalidTicket.body().contains("project_id"));
            assertTrue(invalidTicket.body().contains("reporter_user_id"));
            assertTrue(invalidTicket.body().contains("title"));
            assertTrue(invalidTicket.body().contains("estimate_points"));

            Map<String, Object> ticket = postJson(baseUrl + "/api/tickets", token, Map.of(
                    "project_id", projectId,
                    "reporter_user_id", ownerId,
                    "assignee_user_id", ownerId,
                    "public_ref", "TCK-001",
                    "title", "Provision generated API",
                    "priority", "HIGH",
                    "status", "OPEN",
                    "estimate_points", "5.50",
                    "due_on", "2026-05-01"
            ));
            Object ticketId = requireValue(ticket, "id");

            Map<String, Object> comment = postJson(baseUrl + "/api/ticket_comments", token, Map.of(
                    "ticket_id", ticketId,
                    "author_user_id", ownerId,
                    "body", "Created during integration test",
                    "internal_note", false
            ));
            Object commentId = requireValue(comment, "id");

            Map<String, Object> label = postJson(baseUrl + "/api/labels", token, Map.of(
                    "organization_id", organizationId,
                    "code", "backend",
                    "label", "Backend",
                    "color", "#336699"
            ));
            Object labelId = requireValue(label, "id");

            Map<String, Object> link = postJson(baseUrl + "/api/ticket_labels", token, Map.of(
                    "ticket_id", ticketId,
                    "label_id", labelId,
                    "added_by_user_id", ownerId
            ));
            assertEquals(numberAsLong(ticketId), numberAsLong(requireValue(link, "ticket_id")));
            assertEquals(numberAsLong(labelId), numberAsLong(requireValue(link, "label_id")));

            HttpResponse<String> linkByCompositeId = get(baseUrl + "/api/ticket_labels/" + ticketId + "," + labelId, token);
            assertEquals(200, linkByCompositeId.statusCode(), linkByCompositeId.body());
            JsonNode linkJson = JSON.readTree(linkByCompositeId.body());
            assertEquals(numberAsLong(ticketId), linkJson.get("ticket_id").asLong());
            assertEquals(numberAsLong(labelId), linkJson.get("label_id").asLong());

            Map<String, Object> deployment = postJson(baseUrl + "/api/deployment_events", token, Map.of(
                    "environment_id", environmentId,
                    "deployed_by_user_id", ownerId,
                    "version_tag", "v1.0.0",
                    "status", "SUCCEEDED",
                    "duration_ms", 12345,
                    "metadata", "{\"commit\":\"abc123\"}"
            ));
            assertNotNull(requireValue(deployment, "id"));

            HttpResponse<String> commentsForTicket = get(baseUrl + "/api/ticket_comments?ticket_id=" + ticketId, token);
            assertEquals(200, commentsForTicket.statusCode(), commentsForTicket.body());
            JsonNode commentsPage = JSON.readTree(commentsForTicket.body());
            assertEquals(1, commentsPage.get("totalElements").asInt());
            assertEquals("Created during integration test", commentsPage.at("/content/0/body").asText());

            HttpResponse<String> listTickets = get(baseUrl + "/api/tickets?sort=public_ref", token);
            assertEquals(200, listTickets.statusCode(), listTickets.body());
            JsonNode ticketsPage = JSON.readTree(listTickets.body());
            assertEquals(1, ticketsPage.get("totalElements").asInt());
            assertEquals("TCK-001", ticketsPage.at("/content/0/public_ref").asText());

            Map<String, Object> updatedTicket = putJson(baseUrl + "/api/tickets/" + ticketId, token, Map.of(
                    "title", "Provision generated API - Updated",
                    "status", "DONE"
            ));
            assertEquals("Provision generated API - Updated", updatedTicket.get("title"));

            assertEquals(204, delete(baseUrl + "/api/ticket_labels/" + ticketId + "," + labelId, token).statusCode());
            assertEquals(204, delete(baseUrl + "/api/ticket_comments/" + commentId, token).statusCode());
            HttpResponse<String> deleteTicket = delete(baseUrl + "/api/tickets/" + ticketId, token);
            assertEquals(204, deleteTicket.statusCode(), deleteTicket.body());
            assertEquals(204, delete(baseUrl + "/api/api_keys/" + apiKeyId, token).statusCode());

            HttpResponse<String> afterDelete = get(baseUrl + "/api/tickets", token);
            assertEquals(200, afterDelete.statusCode(), afterDelete.body());
            assertEquals(0, JSON.readTree(afterDelete.body()).get("totalElements").asInt());
        } finally {
            api.destroy();
            if (!api.waitFor(10, TimeUnit.SECONDS)) {
                api.destroyForcibly();
            }
        }
    }

    @Test
    void generatesBuildsStartsAndRejectsErroneousCreatePayloads() throws Exception {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("erroneous-db").toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        executeSqlResource(dbUrl, "schemas/erroneous-creation-schema.sql");

        GenerationJobService service = new GenerationJobService(jobProperties(), new H2SchemaReader());
        String jobId = service.startGeneration(generatorProperties(dbUrl), false, false, false, null);
        JobInfo job = service.getJob(jobId).orElseThrow();

        assertEquals(JobStatus.SUCCEEDED, job.status(), () -> String.join("\n", service.getLogs(jobId, 200)));
        assertTrue(Files.exists(job.zipPath()));
        assertTrue(Files.exists(job.outputDir().resolve("src/main/resources/schema.json")));
        installRuntimeArtifacts();
        packageGeneratedProject(job.outputDir());

        int port = freePort();
        Process api = startGeneratedApi(job.outputDir(), dbUrl, port);
        try {
            String baseUrl = "http://localhost:" + port;
            JsonNode openApi = waitForOpenApi(baseUrl);
            assertTrue(openApi.at("/paths/~1api~1customers").has("post"));
            assertTrue(openApi.at("/paths/~1api~1orders").has("post"));
            assertTrue(openApi.at("/paths/~1api~1order_lines").has("post"));
            assertTrue(openApi.at("/paths/~1api~1order_lines~1{id}").has("get"));

            String token = login(baseUrl);

            HttpResponse<String> invalidCustomer = postJsonRaw(baseUrl + "/api/customers", token, Map.of(
                    "email", "customer@example.test",
                    "full_name", "x".repeat(161),
                    "risk_score", "not-an-int",
                    "extra", "rejected"
            ));
            assertEquals(400, invalidCustomer.statusCode(), invalidCustomer.body());
            assertTrue(invalidCustomer.body().contains("full_name"));
            assertTrue(invalidCustomer.body().contains("risk_score"));
            assertTrue(invalidCustomer.body().contains("extra"));

            Map<String, Object> customer = postJson(baseUrl + "/api/customers", token, Map.of(
                    "email", "customer@example.test",
                    "full_name", "Valid Customer",
                    "risk_score", 7
            ));
            Object customerId = requireValue(customer, "id");

            Map<String, Object> order = postJson(baseUrl + "/api/orders", token, Map.of(
                    "customer_id", customerId,
                    "public_ref", "ORD-001",
                    "status", "PAID",
                    "total_amount", "42.50"
            ));
            Object orderId = requireValue(order, "id");

            HttpResponse<String> invalidLine = postJsonRaw(baseUrl + "/api/order_lines", token, Map.of(
                    "order_id", "bad-id",
                    "sku", "SKU-1",
                    "quantity", "many",
                    "unit_price", "bad-price"
            ));
            assertEquals(400, invalidLine.statusCode(), invalidLine.body());
            assertTrue(invalidLine.body().contains("line_no"));
            assertTrue(invalidLine.body().contains("order_id"));
            assertTrue(invalidLine.body().contains("quantity"));
            assertTrue(invalidLine.body().contains("unit_price"));

            Map<String, Object> line = postJson(baseUrl + "/api/order_lines", token, Map.of(
                    "order_id", orderId,
                    "line_no", 1,
                    "sku", "SKU-1",
                    "quantity", 2,
                    "unit_price", "21.25"
            ));
            assertEquals(numberAsLong(orderId), numberAsLong(requireValue(line, "order_id")));
            assertEquals(1, numberAsLong(requireValue(line, "line_no")));

            HttpResponse<String> lineByCompositeId = get(baseUrl + "/api/order_lines/" + orderId + ",1", token);
            assertEquals(200, lineByCompositeId.statusCode(), lineByCompositeId.body());
            JsonNode lineJson = JSON.readTree(lineByCompositeId.body());
            assertEquals(numberAsLong(orderId), lineJson.get("order_id").asLong());
            assertEquals(1, lineJson.get("line_no").asInt());
        } finally {
            api.destroy();
            if (!api.waitFor(10, TimeUnit.SECONDS)) {
                api.destroyForcibly();
            }
        }
    }

    private GenerationJobProperties jobProperties() {
        GenerationJobProperties props = new GenerationJobProperties();
        props.setTemplatePath(repoRoot().resolve("api-generator-template").toString());
        props.setTempDirectoryPrefix("generated-api-e2e-");
        props.setCommandTimeoutSeconds(180);
        props.setDockerDeploymentEnabled(false);
        props.setOutputFolderName("generated-api");
        props.setZipFileName("generated-api.zip");
        return props;
    }

    private GeneratorProperties generatorProperties(String dbUrl) {
        GeneratorProperties props = new GeneratorProperties();
        props.setAppName("ComplexGeneratedApi");
        props.setBasePackage("com.generated.complex");
        props.getDb().setType(DatabaseType.H2);
        props.getDb().setUrl(dbUrl);
        props.getDb().setUsername("sa");
        props.getDb().setPassword("");
        props.getDb().setSchema("public");
        props.getSecurity().setEnabled(true);
        props.getSecurity().setBootstrapUsername("admin");
        props.getSecurity().setBootstrapPassword("GeneratedAdminPassword123!");
        props.getSecurity().setJwtSecret("H2cA8FXRjnAVgCBcVqt4FZ7FqHKx6VYjzu2ZK9YtFAe");
        props.getSecurity().setJwtIssuer("generated-api-e2e");
        props.getSecurity().setJwtExpirationSeconds(3600);
        props.getFeatures().setGenerateDocker(false);
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

    private Process startGeneratedApi(Path outputDir, String dbUrl, int port) throws IOException {
        Path jar;
        try (var files = Files.list(outputDir.resolve("target"))) {
            jar = files
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().endsWith("-sources.jar"))
                    .filter(path -> !path.getFileName().toString().endsWith("-javadoc.jar"))
                    .max(Comparator.comparingLong(path -> path.toFile().length()))
                    .orElseThrow();
        }

        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar.toString());
        pb.directory(outputDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(outputDir.resolve("generated-api.log").toFile());
        pb.environment().put("PORT", String.valueOf(port));
        pb.environment().put("DB_URL", dbUrl);
        pb.environment().put("DB_USERNAME", "sa");
        pb.environment().put("DB_PASSWORD", "");
        pb.environment().put("DB_TYPE", "H2");
        pb.environment().put("DB_SCHEMA", "public");
        pb.environment().put("BOOTSTRAP_USER", "admin");
        pb.environment().put("BOOTSTRAP_PASSWORD_HASH", new BCryptPasswordEncoder().encode("GeneratedAdminPassword123!"));
        pb.environment().put("JWT_SECRET", "H2cA8FXRjnAVgCBcVqt4FZ7FqHKx6VYjzu2ZK9YtFAe");
        pb.environment().put("JWT_ISSUER", "generated-api-e2e");
        pb.environment().put("JWT_EXP_SECONDS", "3600");
        return pb.start();
    }

    private void installRuntimeArtifacts() throws Exception {
        runCommand(repoRoot(), mavenCommand("-q",
                "-pl", "api-generator-core,api-generator-runtime",
                "-am", "install", "-DskipTests"));
    }

    private void packageGeneratedProject(Path outputDir) throws Exception {
        runCommand(outputDir, mavenCommand("-q", "-DskipTests", "clean", "package"));
    }

    private void runCommand(Path workingDir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        Path log = tempDir.resolve("command-" + Math.abs(String.join("-", command).hashCode()) + ".log");
        pb.redirectOutput(log.toFile());
        Process process = pb.start();
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Command failed (" + process.exitValue() + "): "
                    + String.join(" ", command) + "\n" + Files.readString(log));
        }
    }

    private JsonNode waitForOpenApi(String baseUrl) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> response = get(baseUrl + "/v3/api-docs", null);
                if (response.statusCode() == 200) {
                    return JSON.readTree(response.body());
                }
                last = new IllegalStateException("Unexpected /v3/api-docs status " + response.statusCode() + ": " + response.body());
            } catch (Exception e) {
                last = e;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
        }
        throw new IllegalStateException("Generated API did not expose OpenAPI in time", last);
    }

    private String login(String baseUrl) throws Exception {
        HttpRequest request = jsonRequest(baseUrl + "/auth/login", null, Map.of(
                "username", "admin",
                "password", "GeneratedAdminPassword123!"
        )).POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of(
                "username", "admin",
                "password", "GeneratedAdminPassword123!"
        )), StandardCharsets.UTF_8)).build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        String token = JSON.readTree(response.body()).get("token").asText();
        assertFalse(token.isBlank());
        return token;
    }

    private Map<String, Object> postJson(String url, String token, Map<String, Object> body) throws Exception {
        HttpResponse<String> response = HTTP.send(
                jsonRequest(url, token, body).POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(201, response.statusCode(), response.body());
        return JSON.readValue(response.body(), new TypeReference<>() {});
    }

    private Map<String, Object> putJson(String url, String token, Map<String, Object> body) throws Exception {
        HttpResponse<String> response = HTTP.send(
                jsonRequest(url, token, body).PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readValue(response.body(), new TypeReference<>() {});
    }

    private HttpResponse<String> get(String url, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String url, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).DELETE();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJsonRaw(String url, String token, Map<String, Object> body) throws Exception {
        return HTTP.send(
                jsonRequest(url, token, body).POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpRequest.Builder jsonRequest(String url, String token, Map<String, Object> body) {
        assertNotNull(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Object requireValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        assertNotNull(value, () -> "Missing key '" + key + "' in " + map);
        return value;
    }

    private long numberAsLong(Object value) {
        return assertInstanceOf(Number.class, value, () -> "Expected numeric value, got " + value).longValue();
    }

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
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
        String wrapper = System.getProperty("os.name").toLowerCase().contains("win") ? "mvnw.cmd" : "mvnw";
        return repoRoot().resolve(wrapper);
    }

    private String[] mavenCommand(String... args) {
        Path wrapper = mavenWrapper();
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> command = new ArrayList<>();
        if (!windows && !Files.isExecutable(wrapper)) {
            command.add("bash");
        }
        command.add(wrapper.toString());
        command.addAll(List.of(args));
        return command.toArray(String[]::new);
    }
}
