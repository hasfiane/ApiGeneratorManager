package com.api.generator.reader;

import com.api.generator.schema.DatabaseType;
import java.util.Map;

/**
 * All configuration comes from the YAML the user provides — nothing is auto-detected.
 */
public record SchemaReadRequest(
        DatabaseType                 type,
        String                       url,
        String                       username,
        String                       password,
        String                       schema,
        Map<String, String>          properties,
        Map<String, TableHint>       tableHints
) {

    /**
     * Per-table hints declared by the user in YAML.
     * The reader applies them as-is — it never guesses behaviour from column names.
     *
     * @param softDeleteColumn       column that marks a row as deleted (e.g. "deleted_at")
     * @param createdByColumn        audit column for creator (e.g. "created_by")
     * @param lastModifiedByColumn   audit column for last editor (e.g. "updated_by")
     * @param jsonColumns            columns whose JDBC type should be treated as JSON
     * @param arrayColumns           columns whose JDBC type should be treated as arrays
     */
    public record TableHint(
            String       softDeleteColumn,
            String       createdByColumn,
            String       lastModifiedByColumn,
            java.util.List<String> jsonColumns,
            java.util.List<String> arrayColumns
    ) {}
}
