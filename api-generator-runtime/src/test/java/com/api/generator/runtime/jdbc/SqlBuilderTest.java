package com.api.generator.runtime.jdbc;

import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.ColumnRole;
import com.api.generator.schema.DatabaseType;
import com.api.generator.schema.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlBuilderTest {

    @Test
    void rejectsUnsafeTableIdentifier() {
        SqlBuilder builder = new SqlBuilder(DatabaseType.POSTGRESQL);
        TableInfo table = table("users;drop_table", "id", "email");

        assertThrows(IllegalArgumentException.class, () -> builder.selectByPk(table));
    }

    @Test
    void rejectsUnknownSortColumn() {
        SqlBuilder builder = new SqlBuilder(DatabaseType.POSTGRESQL);
        TableInfo table = table("users", "id", "email");

        assertThrows(IllegalArgumentException.class, () ->
                builder.selectAll(table, List.of(), "email;drop", "ASC"));
    }

    @Test
    void keepsValuesParameterized() {
        SqlBuilder builder = new SqlBuilder(DatabaseType.POSTGRESQL);
        TableInfo table = table("users", "id", "email");

        String sql = builder.selectAll(table, List.of("email"), "id", "DESC");

        assertTrue(sql.contains("\"email\" = :email"));
        assertTrue(sql.contains("ORDER BY \"id\" DESC"));
    }

    @Test
    void filtersSoftDeletedRowsAndSoftDeletesByPrimaryKey() {
        SqlBuilder builder = new SqlBuilder(DatabaseType.POSTGRESQL);
        TableInfo table = table("books", "id", "title", "deleted_at");
        table.getColumns().stream()
                .filter(column -> column.getName().equals("deleted_at"))
                .findFirst()
                .orElseThrow()
                .setRole(ColumnRole.SOFT_DELETE);

        String select = builder.selectAll(table, List.of("title"), "id", "ASC");
        String delete = builder.deleteByPk(table);

        assertTrue(select.contains("\"deleted_at\" IS NULL"));
        assertTrue(select.contains("\"title\" = :title"));
        assertTrue(delete.startsWith("UPDATE \"books\" SET \"deleted_at\" = NOW()"));
        assertTrue(delete.contains("WHERE \"id\" = :pk_id"));
    }

    @Test
    void buildsCompositePrimaryKeyPredicates() {
        SqlBuilder builder = new SqlBuilder(DatabaseType.POSTGRESQL);
        TableInfo table = table("book_tag_links", "book_id", "tag_id", "created_at");
        table.getPrimaryKeys().clear();
        table.getPrimaryKeys().add("book_id");
        table.getPrimaryKeys().add("tag_id");

        String select = builder.selectByPk(table);
        String update = builder.updateByPk(table, List.of("created_at"));
        String delete = builder.deleteByPk(table);

        assertTrue(select.contains("\"book_id\" = :pk_book_id AND \"tag_id\" = :pk_tag_id"));
        assertTrue(update.contains("\"book_id\" = :pk_book_id AND \"tag_id\" = :pk_tag_id"));
        assertTrue(delete.contains("\"book_id\" = :pk_book_id AND \"tag_id\" = :pk_tag_id"));
    }

    private static TableInfo table(String name, String... columns) {
        TableInfo table = new TableInfo(name);
        for (String column : columns) {
            table.getColumns().add(ColumnInfo.builder().name(column).jdbcType("VARCHAR").build());
        }
        table.getPrimaryKeys().add(columns[0]);
        return table;
    }
}
