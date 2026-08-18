package com.api.generator.api.service;

import com.api.generator.reader.H2SchemaReader;
import com.api.generator.reader.SchemaReadRequest;
import com.api.generator.runtime.schema.SchemaRegistry;
import com.api.generator.runtime.validation.SchemaValidator;
import com.api.generator.schema.DatabaseType;
import com.api.generator.schema.TableInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaCreationFixturesTest {

    @TempDir
    Path tempDir;

    @Test
    void complexCreationSchemaIsIntrospectedWithRelationsAndCompositeKeys() throws Exception {
        String dbUrl = h2Url("complex-fixture");
        executeSqlResource(dbUrl, "schemas/complex-creation-schema.sql");

        List<TableInfo> tables = readSchema(dbUrl);

        assertEquals(10, tables.size());
        TableInfo tickets = requireTable(tables, "tickets");
        assertEquals(3, tickets.getForeignKeys().size());
        assertTrue(tickets.getIndexes().stream()
                .anyMatch(index -> index.getColumns().contains("project_id")
                        && index.getColumns().contains("status")));

        TableInfo ticketLabels = requireTable(tables, "ticket_labels");
        assertEquals(2, ticketLabels.getPrimaryKeys().size());
        assertTrue(ticketLabels.getPrimaryKeys().containsAll(List.of("ticket_id", "label_id")));
        assertEquals(3, ticketLabels.getForeignKeys().size());

        TableInfo users = requireTable(tables, "users");
        assertTrue(users.getUniqueColumns().contains("email"));
    }

    @Test
    void erroneousCreationSchemaIsUsableForInvalidCreatePayloadTests() throws Exception {
        String dbUrl = h2Url("broken-fixture");
        executeSqlResource(dbUrl, "schemas/erroneous-creation-schema.sql");

        List<TableInfo> tables = readSchema(dbUrl);

        assertEquals(3, tables.size());
        TableInfo orderLines = requireTable(tables, "order_lines");
        assertEquals(2, orderLines.getPrimaryKeys().size());
        assertTrue(orderLines.getPrimaryKeys().containsAll(List.of("order_id", "line_no")));
        assertEquals(1, orderLines.getForeignKeys().size());
    }

    @Test
    void complexCreationSchemaRejectsInvalidCreatePayloads() throws Exception {
        String dbUrl = h2Url("invalid-create-payloads");
        executeSqlResource(dbUrl, "schemas/complex-creation-schema.sql");

        SchemaRegistry registry = new SchemaRegistry();
        registry.register(readSchema(dbUrl));
        SchemaValidator validator = new SchemaValidator(registry);

        var ticketErrors = validator.validateInsert("tickets", Map.of(
                "public_ref", "TCK-001",
                "title", "x".repeat(221),
                "priority", "HIGH",
                "status", "OPEN",
                "estimate_points", "not-a-decimal",
                "unexpected_field", "rejected"
        ));

        assertTrue(ticketErrors.stream().anyMatch(error -> error.field().equals("project_id")));
        assertTrue(ticketErrors.stream().anyMatch(error -> error.field().equals("reporter_user_id")));
        assertTrue(ticketErrors.stream().anyMatch(error -> error.field().equals("title")));
        assertTrue(ticketErrors.stream().anyMatch(error -> error.field().equals("estimate_points")));
        assertTrue(ticketErrors.stream().anyMatch(error -> error.field().equals("unexpected_field")));

        var apiKeyErrors = validator.validateInsert("api_keys", Map.of(
                "environment_id", "wrong-type",
                "key_name", "x".repeat(121),
                "key_hash", "hash",
                "scopes", "read"
        ));

        assertTrue(apiKeyErrors.stream().anyMatch(error -> error.field().equals("environment_id")));
        assertTrue(apiKeyErrors.stream().anyMatch(error -> error.field().equals("key_name")));
    }

    @Test
    void erroneousCreationSchemaRejectsBadCreatePayloads() throws Exception {
        String dbUrl = h2Url("erroneous-create-payloads");
        executeSqlResource(dbUrl, "schemas/erroneous-creation-schema.sql");

        SchemaRegistry registry = new SchemaRegistry();
        registry.register(readSchema(dbUrl));
        SchemaValidator validator = new SchemaValidator(registry);

        var customerErrors = validator.validateInsert("customers", Map.of(
                "email", "customer@example.test",
                "full_name", "x".repeat(161),
                "risk_score", "not-an-int",
                "extra", "rejected"
        ));

        assertTrue(customerErrors.stream().anyMatch(error -> error.field().equals("full_name")));
        assertTrue(customerErrors.stream().anyMatch(error -> error.field().equals("risk_score")));
        assertTrue(customerErrors.stream().anyMatch(error -> error.field().equals("extra")));

        var lineErrors = validator.validateInsert("order_lines", Map.of(
                "order_id", "bad-id",
                "sku", "SKU-1",
                "quantity", "many",
                "unit_price", "bad-price"
        ));

        assertTrue(lineErrors.stream().anyMatch(error -> error.field().equals("line_no")));
        assertTrue(lineErrors.stream().anyMatch(error -> error.field().equals("order_id")));
        assertTrue(lineErrors.stream().anyMatch(error -> error.field().equals("quantity")));
        assertTrue(lineErrors.stream().anyMatch(error -> error.field().equals("unit_price")));
    }

    private String h2Url(String name) {
        return "jdbc:h2:file:" + tempDir.resolve(name).toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
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

    private List<TableInfo> readSchema(String dbUrl) throws Exception {
        return new H2SchemaReader().readSchema(new SchemaReadRequest(
                DatabaseType.H2,
                dbUrl,
                "sa",
                "",
                "public",
                null,
                null
        ));
    }

    private TableInfo requireTable(List<TableInfo> tables, String name) {
        return tables.stream()
                .filter(table -> table.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing table " + name + " in " + tables));
    }
}
