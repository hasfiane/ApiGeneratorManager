package com.api.generator.runtime.jdbc;

import com.api.generator.schema.DatabaseType;
import com.api.generator.schema.TableInfo;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds parameterized SQL queries. All table/column names are validated against
 * the TableInfo column whitelist to prevent SQL injection.
 */
public class SqlBuilder {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");

    private final DatabaseType dbType;

    public SqlBuilder(DatabaseType dbType) {
        this.dbType = dbType;
    }

    public String selectAll(TableInfo table, List<String> filterCols, String sortCol, String sortDir) {
        validateColumns(table, filterCols);
        StringBuilder sb = new StringBuilder("SELECT * FROM ").append(qt(table.getName()));

        appendWhereClause(sb, table, filterCols);

        if (sortCol != null && !sortCol.isBlank()) {
            validateColumn(table, sortCol);
            sb.append(" ORDER BY ").append(qc(sortCol));
            sb.append("DESC".equalsIgnoreCase(sortDir) ? " DESC" : " ASC");
        }

        sb.append(" LIMIT :limit OFFSET :offset");
        return sb.toString();
    }

    public String selectByPk(TableInfo table) {
        StringBuilder sb = new StringBuilder("SELECT * FROM ").append(qt(table.getName()));
        appendPkWhere(sb, table);
        return sb.toString();
    }

    public String insert(TableInfo table, List<String> cols) {
        validateColumns(table, cols);
        String colList = cols.stream().map(this::qc).collect(Collectors.joining(", "));
        String paramList = cols.stream().map(c -> ":" + c).collect(Collectors.joining(", "));
        return "INSERT INTO " + qt(table.getName()) + " (" + colList + ") VALUES (" + paramList + ")";
    }

    public String updateByPk(TableInfo table, List<String> cols) {
        validateColumns(table, cols);
        String setClauses = cols.stream()
                .map(c -> qc(c) + " = :" + c)
                .collect(Collectors.joining(", "));
        StringBuilder sb = new StringBuilder("UPDATE ").append(qt(table.getName()))
                .append(" SET ").append(setClauses);
        appendPkWhere(sb, table);
        return sb.toString();
    }

    public String deleteByPk(TableInfo table) {
        return table.softDeleteColumn().map(sdCol -> {
            // Soft-delete : mettre le timestamp à NOW() plutôt que supprimer la ligne
            StringBuilder sb = new StringBuilder("UPDATE ").append(qt(table.getName()))
                    .append(" SET ").append(qc(sdCol.getName())).append(" = NOW()");
            appendPkWhere(sb, table);
            return sb.toString();
        }).orElseGet(() -> {
            StringBuilder sb = new StringBuilder("DELETE FROM ").append(qt(table.getName()));
            appendPkWhere(sb, table);
            return sb.toString();
        });
    }

    public String count(TableInfo table, List<String> filterCols) {
        validateColumns(table, filterCols);
        StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM ").append(qt(table.getName()));
        appendWhereClause(sb, table, filterCols);
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void appendPkWhere(StringBuilder sb, TableInfo table) {
        List<String> pks = table.getPrimaryKeys();
        if (pks.isEmpty()) throw new IllegalStateException("Table " + table.getName() + " has no primary key");
        sb.append(" WHERE ");
        for (int i = 0; i < pks.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(qc(pks.get(i))).append(" = :pk_").append(pks.get(i));
        }
    }

    private void appendWhereClause(StringBuilder sb, TableInfo table, List<String> filterCols) {
        boolean hasFilter = filterCols != null && !filterCols.isEmpty();
        var sdCol = table.softDeleteColumn(); // Optional<ColumnInfo>

        if (!hasFilter && sdCol.isEmpty()) return;

        sb.append(" WHERE ");
        boolean first = true;

        if (sdCol.isPresent()) {
            // deleted_at est un TIMESTAMP — NULL = non supprimé, non-NULL = soft-deleted
            sb.append(qc(sdCol.get().getName())).append(" IS NULL");
            first = false;
        }

        if (hasFilter) {
            for (String col : filterCols) {
                if (!first) sb.append(" AND ");
                sb.append(qc(col)).append(" = :").append(col);
                first = false;
            }
        }
    }

    /** Quote table name — dialect aware */
    private String qt(String name) {
        validateIdentifier(name);
        return dbType == DatabaseType.MYSQL ? "`" + name + "`" : "\"" + name + "\"";
    }

    /** Quote column name — dialect aware */
    private String qc(String name) {
        validateIdentifier(name);
        return dbType == DatabaseType.MYSQL ? "`" + name + "`" : "\"" + name + "\"";
    }

    private void validateIdentifier(String name) {
        if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier rejected");
        }
    }

    private void validateColumn(TableInfo table, String col) {
        Set<String> valid = table.getColumns().stream()
                .map(c -> c.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!valid.contains(col.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unknown column '" + col + "' for table '" + table.getName() + "'");
        }
    }

    private void validateColumns(TableInfo table, List<String> cols) {
        if (cols == null || cols.isEmpty()) return;
        for (String col : cols) {
            validateColumn(table, col);
        }
    }
}
