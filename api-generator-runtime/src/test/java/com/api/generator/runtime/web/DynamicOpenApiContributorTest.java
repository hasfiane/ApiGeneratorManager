package com.api.generator.runtime.web;

import com.api.generator.runtime.schema.ManifestRegistry;
import com.api.generator.runtime.schema.SchemaRegistry;
import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.TableInfo;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicOpenApiContributorTest {

    @Test
    void contributesCrudPathsAndSchemasForRegisteredTables() {
        SchemaRegistry schemaRegistry = new SchemaRegistry();
        schemaRegistry.register(List.of(table()));
        OpenAPI openApi = new OpenAPI();

        new DynamicOpenApiContributor(schemaRegistry, new ManifestRegistry()).customise(openApi);

        assertNotNull(openApi.getPaths().get("/api/books").getGet());
        assertNotNull(openApi.getPaths().get("/api/books").getPost());
        assertNotNull(openApi.getPaths().get("/api/books/{id}").getGet());
        assertNotNull(openApi.getPaths().get("/api/books/{id}").getPut());
        assertNotNull(openApi.getPaths().get("/api/books/{id}").getDelete());
        assertTrue(openApi.getComponents().getSchemas().containsKey("Books"));
        assertTrue(openApi.getComponents().getSchemas().get("Books").getProperties().containsKey("published_on"));
    }

    private static TableInfo table() {
        TableInfo table = new TableInfo("books");
        table.getPrimaryKeys().add("id");
        table.getColumns().add(ColumnInfo.builder().name("id").jdbcType("BIGINT").build());
        table.getColumns().add(ColumnInfo.builder().name("title").jdbcType("VARCHAR").build());
        table.getColumns().add(ColumnInfo.builder().name("published_on").jdbcType("DATE").build());
        table.getColumns().add(ColumnInfo.builder().name("in_print").jdbcType("BOOLEAN").build());
        return table;
    }
}
