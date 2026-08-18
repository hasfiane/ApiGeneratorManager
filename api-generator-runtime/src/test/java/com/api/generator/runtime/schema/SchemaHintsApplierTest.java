package com.api.generator.runtime.schema;

import com.api.generator.runtime.config.RuntimeTableProperties;
import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.ColumnRole;
import com.api.generator.schema.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaHintsApplierTest {

    @Test
    void appliesConfiguredColumnRolesAndFlags() {
        TableInfo table = table();
        SchemaRegistry registry = new SchemaRegistry();
        registry.register(List.of(table));
        RuntimeTableProperties properties = new RuntimeTableProperties();
        RuntimeTableProperties.TableHint hint = new RuntimeTableProperties.TableHint();
        hint.setSoftDeleteColumn("deleted_at");
        hint.setCreatedByColumn("created_by");
        hint.setLastModifiedByColumn("updated_by");
        hint.setCreatedAtColumn("created_at");
        hint.setUpdatedAtColumn("updated_at");
        hint.setJsonColumns(List.of("metadata"));
        hint.setArrayColumns(List.of("tags"));
        properties.setTables(Map.of("books", hint));

        new SchemaHintsApplier(registry, properties).apply();

        assertEquals(ColumnRole.SOFT_DELETE, column(table, "deleted_at").getRole());
        assertEquals(ColumnRole.CREATED_BY, column(table, "created_by").getRole());
        assertEquals(ColumnRole.LAST_MODIFIED_BY, column(table, "updated_by").getRole());
        assertEquals(ColumnRole.CREATED_AT, column(table, "created_at").getRole());
        assertEquals(ColumnRole.UPDATED_AT, column(table, "updated_at").getRole());
        assertTrue(column(table, "metadata").isJson());
        assertTrue(column(table, "tags").isArray());
    }

    @Test
    void noopsWhenNoHintsAreConfigured() {
        TableInfo table = table();
        SchemaRegistry registry = new SchemaRegistry();
        registry.register(List.of(table));

        new SchemaHintsApplier(registry, new RuntimeTableProperties()).apply();

        assertEquals(ColumnRole.NONE, column(table, "deleted_at").getRole());
    }

    private static TableInfo table() {
        TableInfo table = new TableInfo("books");
        for (String name : List.of("deleted_at", "created_by", "updated_by", "created_at", "updated_at", "metadata", "tags")) {
            table.getColumns().add(ColumnInfo.builder().name(name).jdbcType("VARCHAR").build());
        }
        return table;
    }

    private static ColumnInfo column(TableInfo table, String name) {
        return table.getColumns().stream().filter(column -> column.getName().equals(name)).findFirst().orElseThrow();
    }
}
