package com.api.generator.api.service;

import com.api.generator.config.GenerationJobProperties;
import com.api.generator.config.GeneratorProperties;
import com.api.generator.reader.SchemaReader;
import com.api.generator.schema.DatabaseType;
import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.ForeignKeyInfo;
import com.api.generator.schema.TableInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationJobServiceGeneratedSecurityConfigTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @TempDir
    Path tempDir;

    @Test
    void generatedApplicationYmlRequiresEnvironmentSecretsInsteadOfEmbeddingFallbacks() throws Exception {
        GenerationJobService service = new GenerationJobService(new GenerationJobProperties(), null);
        GeneratorProperties props = new GeneratorProperties();
        props.getDb().setType(DatabaseType.POSTGRESQL);
        props.getSecurity().setEnabled(true);
        props.getSecurity().setBootstrapUsername("admin");
        props.getSecurity().setBootstrapPassword("admin123456789");
        props.getSecurity().setJwtSecret("local-generated-api-jwt-secret-32chars");
        props.getSecurity().setJwtIssuer("generated-api");

        ReflectionTestUtils.invokeMethod(service, "writeApplicationYml", tempDir, props);

        Path applicationYml = tempDir.resolve("src/main/resources/application.yml");
        assertTrue(Files.exists(applicationYml));

        Map<String, Object> yaml = YAML.readValue(applicationYml.toFile(), new TypeReference<>() {});
        Map<String, Object> security = castMap(yaml.get("security"));
        Map<String, Object> bootstrap = castMap(security.get("bootstrap"));
        Map<String, Object> jwt = castMap(security.get("jwt"));

        String bootstrapPassword = String.valueOf(bootstrap.get("password"));
        assertEquals("${BOOTSTRAP_USER:}", bootstrap.get("username"));
        assertEquals("${BOOTSTRAP_PASSWORD_HASH:}", bootstrapPassword);
        assertFalse(bootstrapPassword.contains("admin123456789"));
        assertEquals("${JWT_SECRET}", jwt.get("secret"));
        assertFalse(Files.readString(applicationYml).contains("local-generated-api-jwt-secret-32chars"));
    }

    @Test
    void rejectsGeneratedJwtSecretWithPlaceholderControlCharacters() {
        GenerationJobService service = new GenerationJobService(new GenerationJobProperties(), null);
        GeneratorProperties props = new GeneratorProperties();
        props.getDb().setType(DatabaseType.POSTGRESQL);
        props.getSecurity().setEnabled(true);
        props.getSecurity().setBootstrapUsername("admin");
        props.getSecurity().setBootstrapPassword("admin123456789");
        props.getSecurity().setJwtSecret("bad-generated-api-jwt-secret-32chars}");
        props.getSecurity().setJwtIssuer("generated-api");

        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "writeApplicationYml", tempDir, props));
    }

    @Test
    void generatedZipContainsEnterpriseSupportFilesWithoutSensitiveFallbacks() throws Exception {
        GenerationJobProperties jobProperties = new GenerationJobProperties();
        jobProperties.setTemplatePath(repoRoot().resolve("api-generator-template").toString());
        jobProperties.setTempDirectoryPrefix("generated-api-support-files-");
        jobProperties.setOutputFolderName("generated-api");
        jobProperties.setZipFileName("generated-api.zip");
        jobProperties.setDockerDeploymentEnabled(false);

        SchemaReader schemaReader = mock(SchemaReader.class);
        when(schemaReader.readSchema(any())).thenReturn(List.of(new TableInfo("customers")));

        GenerationJobService service = new GenerationJobService(jobProperties, schemaReader);
        GeneratorProperties props = new GeneratorProperties();
        props.setAppName("CustomerApi");
        props.setBasePackage("com.example.postgresapi");
        props.getDb().setType(DatabaseType.POSTGRESQL);
        props.getDb().setUrl("jdbc:postgresql://db.example.test:5432/customer_api");
        props.getDb().setUsername("customer_api_user");
        props.getDb().setPassword("unit-test-db-password");
        props.getDb().setSchema("public");
        props.getSecurity().setEnabled(true);
        props.getSecurity().setBootstrapUsername("admin@example.test");
        props.getSecurity().setBootstrapPassword("GeneratedAdminPassword123!");
        props.getSecurity().setJwtSecret("unit-test-jwt-secret-with-32-characters");
        props.getSecurity().setJwtIssuer("customer-api");
        props.getFeatures().setGenerateDocker(true);

        String jobId = service.startGeneration(props, false, false, false, null);
        JobInfo job = service.getJob(jobId).orElseThrow();

        assertEquals(JobStatus.SUCCEEDED, job.status(), () -> String.join("\n", service.getLogs(jobId, 200)));
        Map<String, String> zipEntries = readZipEntries(job.zipPath());

        assertTrue(zipEntries.containsKey("README.md"));
        assertTrue(zipEntries.containsKey(".env.example"));
        assertTrue(zipEntries.containsKey(".gitignore"));
        assertTrue(zipEntries.containsKey("docs/customization.md"));
        assertTrue(zipEntries.containsKey("docs/security.md"));
        assertTrue(zipEntries.containsKey("docs/runtime-model.md"));
        assertTrue(zipEntries.containsKey("src/main/resources/schema.json"));
        assertTrue(zipEntries.containsKey("src/main/java/com/example/postgresapi/GeneratedApiApplication.java"));
        assertTrue(zipEntries.containsKey("src/test/java/com/example/postgresapi/GeneratedApiApplicationTests.java"));
        assertFalse(zipEntries.containsKey(".env"));
        assertFalse(zipEntries.containsKey("src/main/java/com/example/api/GeneratedApiApplication.java"));
        assertFalse(zipEntries.containsKey("docker-compose.manager.yml"));

        assertTrue(zipEntries.get("README.md").contains("# CustomerApi"));
        assertTrue(zipEntries.get("README.md").contains("Recommended package: `com.example.postgresapi.custom`."));
        assertTrue(zipEntries.get(".env.example").contains("JWT_SECRET=replace-with-a-strong-secret"));
        assertTrue(zipEntries.get(".gitignore").contains(".env"));
        assertFalse(zipEntries.get(".gitignore").contains("schema.json"));
        assertTrue(zipEntries.get("docs/customization.md").contains("package com.example.postgresapi.custom;"));
        assertTrue(zipEntries.get("docs/security.md").contains("Do not commit `.env`."));
        assertTrue(zipEntries.get("docs/runtime-model.md").contains("runtime-driven model"));
        assertTrue(zipEntries.get("src/main/java/com/example/postgresapi/GeneratedApiApplication.java")
                .contains("package com.example.postgresapi;"));
        assertTrue(zipEntries.get("src/test/java/com/example/postgresapi/GeneratedApiApplicationTests.java")
                .contains("schemaJsonIsPackaged"));

        String applicationYml = zipEntries.get("src/main/resources/application.yml");
        assertTrue(applicationYml.contains("${JWT_SECRET}"));
        assertFalse(applicationYml.contains("${JWT_SECRET:"));
        assertFalse(applicationYml.contains("unit-test-jwt-secret-with-32-characters"));
        assertFalse(applicationYml.contains("GeneratedAdminPassword123!"));
        assertFalse(applicationYml.contains("$2"));
        assertFalse(applicationYml.contains("unit-test-db-password"));

        String allGeneratedText = String.join("\n", zipEntries.values()).toLowerCase();
        assertFalse(allGeneratedText.contains("aiven"));
        assertFalse(allGeneratedText.contains("apigen-manager-prod_internal"));
        assertFalse(allGeneratedText.contains("api_manager_network"));
        assertFalse(allGeneratedText.contains("api_container_name"));
        assertFalse(allGeneratedText.contains("c:\\users\\"));
        assertFalse(allGeneratedText.contains("/home/"));
    }

    @Test
    void schemaFileGenerationWritesNormalizedSchemaJsonWithoutJdbcReader() throws Exception {
        GenerationJobProperties jobProperties = new GenerationJobProperties();
        jobProperties.setTemplatePath(repoRoot().resolve("api-generator-template").toString());
        jobProperties.setTempDirectoryPrefix("generated-api-yaml-schema-");
        jobProperties.setOutputFolderName("generated-api");
        jobProperties.setZipFileName("generated-api.zip");
        jobProperties.setDockerDeploymentEnabled(false);

        SchemaReader schemaReader = mock(SchemaReader.class);
        GenerationJobService service = new GenerationJobService(jobProperties, schemaReader);

        TableInfo customers = new TableInfo("customers");
        customers.getColumns().add(ColumnInfo.builder()
                .name("id")
                .jdbcType("uuid")
                .nullable(false)
                .ordinalPosition(1)
                .build());
        customers.getColumns().add(ColumnInfo.builder()
                .name("email")
                .jdbcType("varchar")
                .size(255)
                .nullable(false)
                .unique(true)
                .ordinalPosition(2)
                .build());
        customers.getPrimaryKeys().add("id");
        customers.getUniqueColumns().add("email");

        TableInfo orders = new TableInfo("orders");
        orders.getColumns().add(ColumnInfo.builder()
                .name("id")
                .jdbcType("uuid")
                .nullable(false)
                .ordinalPosition(1)
                .build());
        orders.getColumns().add(ColumnInfo.builder()
                .name("customer_id")
                .jdbcType("uuid")
                .nullable(false)
                .ordinalPosition(2)
                .build());
        orders.getPrimaryKeys().add("id");
        orders.getForeignKeys().add(new ForeignKeyInfo("customer_id", "customers", "id"));
        customers.getReferencedBy().add(new ForeignKeyInfo("id", "orders", "customer_id"));

        GeneratorProperties props = new GeneratorProperties();
        props.setAppName("CrmApi");
        props.setBasePackage("com.example.crm");
        props.getDb().setType(DatabaseType.POSTGRESQL);
        props.getDb().setSchema("public");
        props.setSchemaTables(List.of(customers, orders));
        props.getSecurity().setEnabled(true);
        props.getSecurity().setBootstrapUsername("admin@example.test");
        props.getSecurity().setBootstrapPassword("GeneratedAdminPassword123!");
        props.getSecurity().setJwtSecret("unit-test-jwt-secret-with-32-characters");
        props.getSecurity().setJwtIssuer("crm-api");

        String jobId = service.startGeneration(props, false, false, false, null);
        JobInfo job = service.getJob(jobId).orElseThrow();

        assertEquals(JobStatus.SUCCEEDED, job.status(), () -> String.join("\n", service.getLogs(jobId, 200)));
        Map<String, String> zipEntries = readZipEntries(job.zipPath());
        assertTrue(zipEntries.containsKey("src/main/resources/schema.json"));
        assertTrue(zipEntries.get("src/main/resources/schema.json").contains("\"name\" : \"customers\""));
        assertTrue(zipEntries.get("src/main/resources/schema.json").contains("\"fkColumn\" : \"customer_id\""));
        assertFalse(zipEntries.containsKey("schema.yaml"));
        assertFalse(zipEntries.containsKey("schema.yml"));
        verify(schemaReader, never()).readSchema(any());
    }

    private static Map<String, String> readZipEntries(Path zipPath) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        }
        return entries;
    }

    private static Path repoRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("api-generator-template"))
                    && Files.exists(current.resolve("api-generator-back"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
