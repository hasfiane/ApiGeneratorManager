package com.api.generator.reader;

import com.api.generator.schema.*;
import java.sql.*;
import java.util.*;

public abstract class AbstractJdbcSchemaReader implements SchemaReader {

    protected abstract String[] tableTypes();

    @Override
    public List<TableInfo> readSchema(SchemaReadRequest req) throws Exception {
        Class.forName(req.type().getDriverClass());
        Properties p = new Properties();
        p.put("user", req.username());
        if (req.password() != null) p.put("password", req.password());
        if (req.properties() != null) p.putAll(req.properties());

        try (Connection c = DriverManager.getConnection(req.url(), p)) {
            DatabaseMetaData m = c.getMetaData();
            String cat = c.getCatalog(), sch = req.schema();
            Map<String, TableInfo> tables = new LinkedHashMap<>();
            try (ResultSet rs = m.getTables(cat, sch, "%", tableTypes())) {
                while (rs.next()) {
                    String n = rs.getString("TABLE_NAME");
                    tables.put(n, new TableInfo(n));
                }
            }
            for (TableInfo t : tables.values()) {
                loadColumns(m, cat, sch, t);
                loadPKs(m, cat, sch, t);
                loadFKs(m, cat, sch, t);
                loadIndexes(m, cat, sch, t);
                applyHints(t, req.tableHints());
            }
            buildReverseRels(tables);
            return new ArrayList<>(tables.values());
        }
    }

    /**
     * Applies user-declared hints from YAML — no code-level pattern detection.
     * Everything is opt-in via the YAML the user provides through the front-end.
     */
    private void applyHints(TableInfo t, Map<String, SchemaReadRequest.TableHint> hints) {
        if (hints == null) return;
        SchemaReadRequest.TableHint hint = hints.get(t.getName().toLowerCase(Locale.ROOT));
        if (hint == null) return;

        // Soft-delete : on affecte ColumnRole.SOFT_DELETE à la colonne concernée
        if (hint.softDeleteColumn() != null) {
            t.getColumns().stream()
                .filter(c -> c.getName().equalsIgnoreCase(hint.softDeleteColumn()))
                .findFirst()
                .ifPresent(c -> c.setRole(com.api.generator.schema.ColumnRole.SOFT_DELETE));
        }
        // createdBy : ColumnRole.CREATED_BY
        if (hint.createdByColumn() != null) {
            t.getColumns().stream()
                .filter(c -> c.getName().equalsIgnoreCase(hint.createdByColumn()))
                .findFirst()
                .ifPresent(c -> c.setRole(com.api.generator.schema.ColumnRole.CREATED_BY));
        }
        // lastModifiedBy : ColumnRole.LAST_MODIFIED_BY
        if (hint.lastModifiedByColumn() != null) {
            t.getColumns().stream()
                .filter(c -> c.getName().equalsIgnoreCase(hint.lastModifiedByColumn()))
                .findFirst()
                .ifPresent(c -> c.setRole(com.api.generator.schema.ColumnRole.LAST_MODIFIED_BY));
        }
        // JSON columns
        if (hint.jsonColumns() != null && !hint.jsonColumns().isEmpty()) {
            t.getColumns().stream()
                .filter(c -> hint.jsonColumns().contains(c.getName()))
                .forEach(c -> c.setJson(true));
        }
        // Array columns
        if (hint.arrayColumns() != null && !hint.arrayColumns().isEmpty()) {
            t.getColumns().stream()
                .filter(c -> hint.arrayColumns().contains(c.getName()))
                .forEach(c -> { c.setArray(true); c.setArrayComponentType(c.getJdbcType()); });
        }
    }

    private void loadColumns(DatabaseMetaData m, String cat, String sch, TableInfo t) throws SQLException {
        try (ResultSet rs = m.getColumns(cat, sch, t.getName(), "%")) {
            while (rs.next()) t.getColumns().add(ColumnInfo.builder()
                .name(rs.getString("COLUMN_NAME"))
                .jdbcType(rs.getString("TYPE_NAME"))
                .size(rs.getInt("COLUMN_SIZE"))
                .nullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable)
                .autoIncrement("YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")))
                .defaultValue(rs.getString("COLUMN_DEF"))
                .decimalDigits(rs.getInt("DECIMAL_DIGITS"))
                .ordinalPosition(rs.getInt("ORDINAL_POSITION"))
                .remarks(rs.getString("REMARKS"))
                .build());
        }
    }

    private void loadPKs(DatabaseMetaData m, String cat, String sch, TableInfo t) throws SQLException {
        try (ResultSet rs = m.getPrimaryKeys(cat, sch, t.getName()))
            { while (rs.next()) t.getPrimaryKeys().add(rs.getString("COLUMN_NAME")); }
    }

    private void loadFKs(DatabaseMetaData m, String cat, String sch, TableInfo t) throws SQLException {
        try (ResultSet rs = m.getImportedKeys(cat, sch, t.getName()))
            { while (rs.next()) t.getForeignKeys().add(new ForeignKeyInfo(
                rs.getString("FKCOLUMN_NAME"), rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME"))); }
    }

    private void loadIndexes(DatabaseMetaData m, String cat, String sch, TableInfo t) throws SQLException {
        Map<String, IndexInfo> map = new LinkedHashMap<>();
        try (ResultSet rs = m.getIndexInfo(cat, sch, t.getName(), false, false)) {
            while (rs.next()) {
                String n = rs.getString("INDEX_NAME"); if (n == null) continue;
                boolean nu = rs.getBoolean("NON_UNIQUE"); short tp = rs.getShort("TYPE");
                IndexInfo idx = map.computeIfAbsent(n, k -> IndexInfo.builder().indexName(n).unique(!nu)
                    .type(tp == DatabaseMetaData.tableIndexClustered ? "CLUSTERED" : "BTREE").build());
                idx.getColumns().add(rs.getString("COLUMN_NAME"));
                if (!nu) t.getUniqueColumns().add(rs.getString("COLUMN_NAME"));
            }
        }
        t.getIndexes().addAll(map.values());
    }

    private void buildReverseRels(Map<String, TableInfo> tables) {
        for (TableInfo t : tables.values())
            for (ForeignKeyInfo fk : t.getForeignKeys()) {
                TableInfo ref = tables.get(fk.getPkTable());
                if (ref != null) ref.getReferencedBy().add(
                    new ForeignKeyInfo(fk.getPkColumn(), t.getName(), fk.getFkColumn()));
            }
    }
}

