package com.api.generator.runtime.validation;

import com.api.generator.runtime.error.ValidationError;
import com.api.generator.runtime.schema.SchemaRegistry;
import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaValidatorTest {

    @Test
    void validatesRequiredUnknownLengthEnumAndTypeErrors() {
        SchemaValidator validator = new SchemaValidator(registry());

        List<ValidationError.FieldError> errors = validator.validateInsert("books", Map.of(
                "unknown", "value",
                "title", "A title that is too long",
                "status", "BROKEN",
                "pages", "not-a-number"
        ));

        assertEquals(5, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.field().equals("unknown")));
        assertTrue(errors.stream().anyMatch(error -> error.field().equals("author_id")));
        assertTrue(errors.stream().anyMatch(error -> error.field().equals("title")));
        assertTrue(errors.stream().anyMatch(error -> error.field().equals("status")));
        assertTrue(errors.stream().anyMatch(error -> error.field().equals("pages")));
    }

    @Test
    void acceptsValidInsertAndUpdatePayloads() {
        SchemaValidator validator = new SchemaValidator(registry());

        assertTrue(validator.validateInsert("books", Map.of(
                "title", "Kindred",
                "author_id", 1,
                "status", "PUBLISHED",
                "pages", "264"
        )).isEmpty());
        assertTrue(validator.validateUpdate("books", Map.of(
                "title", "Updated",
                "pages", 265
        )).isEmpty());
    }

    @Test
    void ignoresUnknownTable() {
        SchemaValidator validator = new SchemaValidator(registry());

        assertTrue(validator.validateInsert("missing", Map.of("field", "value")).isEmpty());
        assertTrue(validator.validateUpdate("missing", Map.of("field", "value")).isEmpty());
    }

    private static SchemaRegistry registry() {
        TableInfo table = new TableInfo("books");
        table.getPrimaryKeys().add("id");
        table.getColumns().add(ColumnInfo.builder().name("id").jdbcType("BIGINT").nullable(false).autoIncrement(true).build());
        table.getColumns().add(ColumnInfo.builder().name("title").jdbcType("VARCHAR").size(10).nullable(false).build());
        table.getColumns().add(ColumnInfo.builder().name("author_id").jdbcType("INTEGER").nullable(false).build());
        table.getColumns().add(ColumnInfo.builder().name("status").jdbcType("VARCHAR").nullable(false).enumType(true).enumValues(new String[]{"DRAFT", "PUBLISHED"}).build());
        table.getColumns().add(ColumnInfo.builder().name("pages").jdbcType("INTEGER").nullable(true).build());
        SchemaRegistry registry = new SchemaRegistry();
        registry.register(List.of(table));
        return registry;
    }
}
