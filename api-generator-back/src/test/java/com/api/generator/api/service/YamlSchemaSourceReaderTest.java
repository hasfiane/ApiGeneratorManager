package com.api.generator.api.service;

import com.api.generator.schema.DatabaseType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlSchemaSourceReaderTest {

    private final YamlSchemaSourceReader reader = new YamlSchemaSourceReader();

    @Test
    void readsValidYamlSchemaIntoNormalizedTables() {
        YamlSchemaSourceReader.NormalizedYamlSchema schema = reader.read(file("schema.yaml", """
                app:
                  name: crm-api
                  packageName: com.example.crm
                database:
                  type: postgresql
                  schema: public
                tables:
                  customers:
                    columns:
                      id:
                        type: uuid
                        primaryKey: true
                        nullable: false
                      email:
                        type: varchar
                        length: 255
                        unique: true
                        nullable: false
                  orders:
                    columns:
                      id:
                        type: uuid
                        primaryKey: true
                        nullable: false
                      customer_id:
                        type: uuid
                        nullable: false
                        references:
                          table: customers
                          column: id
                      total:
                        type: decimal
                        precision: 10
                        scale: 2
                """));

        assertEquals("crm-api", schema.appName());
        assertEquals("com.example.crm", schema.packageName());
        assertEquals(DatabaseType.POSTGRESQL, schema.databaseType());
        assertEquals(2, schema.tables().size());
        assertEquals("customers", schema.tables().get(0).getName());
        assertEquals("id", schema.tables().get(0).getPrimaryKeys().get(0));
        assertTrue(schema.tables().get(0).getUniqueColumns().contains("email"));
        assertEquals("customer_id", schema.tables().get(1).getForeignKeys().get(0).getFkColumn());
        assertEquals("orders", schema.tables().get(0).getReferencedBy().get(0).getPkTable());
    }

    @Test
    void rejectsInvalidYaml() {
        assertBadRequest("YAML_SCHEMA_INVALID", file("schema.yaml", "tables: ["));
    }

    @Test
    void rejectsTxtExtension() {
        assertBadRequest("YAML_SCHEMA_INVALID_EXTENSION", file("schema.txt", "tables: {}"));
    }

    @Test
    void rejectsEmptyFile() {
        assertBadRequest("YAML_SCHEMA_EMPTY", file("schema.yaml", ""));
    }

    @Test
    void rejectsTableWithoutColumns() {
        assertBadRequest("YAML_SCHEMA_TABLE_WITHOUT_COLUMNS", file("schema.yaml", """
                tables:
                  customers:
                    columns: {}
                """));
    }

    @Test
    void rejectsColumnWithoutType() {
        assertBadRequest("YAML_SCHEMA_COLUMN_TYPE_MISSING", file("schema.yaml", """
                tables:
                  customers:
                    columns:
                      id:
                        nullable: false
                """));
    }

    @Test
    void rejectsInvalidTableName() {
        assertBadRequest("YAML_SCHEMA_INVALID_TABLE_NAME", file("schema.yaml", """
                tables:
                  1customers:
                    columns:
                      id:
                        type: uuid
                """));
    }

    @Test
    void rejectsInvalidColumnName() {
        assertBadRequest("YAML_SCHEMA_INVALID_COLUMN_NAME", file("schema.yaml", """
                tables:
                  customers:
                    columns:
                      bad-column:
                        type: uuid
                """));
    }

    @Test
    void rejectsForeignKeyToUnknownTable() {
        assertBadRequest("YAML_SCHEMA_UNKNOWN_FOREIGN_KEY", file("schema.yaml", """
                tables:
                  orders:
                    columns:
                      customer_id:
                        type: uuid
                        references:
                          table: customers
                          column: id
                """));
    }

    @Test
    void rejectsForeignKeyToUnknownColumn() {
        assertBadRequest("YAML_SCHEMA_UNKNOWN_FOREIGN_KEY", file("schema.yaml", """
                tables:
                  customers:
                    columns:
                      id:
                        type: uuid
                  orders:
                    columns:
                      customer_id:
                        type: uuid
                        references:
                          table: customers
                          column: missing_id
                """));
    }

    @Test
    void rejectsInvalidPackageName() {
        assertBadRequest("YAML_SCHEMA_INVALID_PACKAGE_NAME", file("schema.yaml", """
                app:
                  packageName: Com.Example
                tables:
                  customers:
                    columns:
                      id:
                        type: uuid
                """));
    }

    private void assertBadRequest(String expectedReason, MockMultipartFile file) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> reader.read(file));
        assertEquals(400, ex.getStatusCode().value());
        assertEquals(expectedReason, ex.getReason());
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/x-yaml", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
