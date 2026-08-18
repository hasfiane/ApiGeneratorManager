package com.api.generator.runtime.schema;

import com.api.generator.runtime.config.RuntimeTableProperties;
import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.ColumnRole;
import com.api.generator.schema.TableInfo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads per-table hints from {@code application.yml} (under {@code generator.tables})
 * and assigns a {@link ColumnRole} to the matching {@link ColumnInfo}.
 *
 * <p>This is the <b>only</b> place where roles are set. No column name is hard-coded
 * anywhere else in the runtime — a column with role {@code SOFT_DELETE} is just a plain
 * column as far as SQL is concerned; the role only tells the runtime what value to inject.</p>
 */
public class SchemaHintsApplier {

    private static final Logger log = LoggerFactory.getLogger(SchemaHintsApplier.class);

    private final SchemaRegistry registry;
    private final RuntimeTableProperties tableProperties;

    public SchemaHintsApplier(SchemaRegistry registry, RuntimeTableProperties tableProperties) {
        this.registry = registry;
        this.tableProperties = tableProperties;
    }

    @PostConstruct
    public void apply() {
        if (tableProperties.getTables() == null || tableProperties.getTables().isEmpty()) {
            log.debug("No table hints configured — skipping role assignment.");
            return;
        }
        tableProperties.getTables().forEach((tableName, hint) ->
                registry.getTable(tableName).ifPresentOrElse(
                        t -> applyHints(t, hint),
                        () -> log.warn("Hint for unknown table '{}' ignored.", tableName)
                )
        );
        log.info("Column roles applied for tables: {}", tableProperties.getTables().keySet());
    }

    private void applyHints(TableInfo table, RuntimeTableProperties.TableHint hint) {

        assignRole(table, hint.getSoftDeleteColumn(),     ColumnRole.SOFT_DELETE);
        assignRole(table, hint.getCreatedByColumn(),      ColumnRole.CREATED_BY);
        assignRole(table, hint.getLastModifiedByColumn(), ColumnRole.LAST_MODIFIED_BY);
        assignRole(table, hint.getCreatedAtColumn(),      ColumnRole.CREATED_AT);
        assignRole(table, hint.getUpdatedAtColumn(),      ColumnRole.UPDATED_AT);

        if (hint.getJsonColumns() != null) {
            hint.getJsonColumns().forEach(n -> findColumn(table, n).ifPresent(c -> c.setJson(true)));
        }
        if (hint.getArrayColumns() != null) {
            hint.getArrayColumns().forEach(n -> findColumn(table, n).ifPresent(c -> c.setArray(true)));
        }
    }

    private void assignRole(TableInfo table, String colName, ColumnRole role) {
        if (colName == null || colName.isBlank()) return;
        findColumn(table, colName).ifPresentOrElse(
                col -> {
                    col.setRole(role);
                    log.debug("Table '{}': column '{}' → role {}", table.getName(), colName, role);
                },
                () -> log.warn("Table '{}': hint column '{}' not found in schema — ignored.",
                        table.getName(), colName)
        );
    }

    private java.util.Optional<ColumnInfo> findColumn(TableInfo table, String colName) {
        return table.getColumns().stream()
                .filter(c -> c.getName().equalsIgnoreCase(colName))
                .findFirst();
    }
}
