package com.api.generator.runtime.jdbc;

import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.TableInfo;
import com.api.generator.runtime.error.NotFoundException;
import com.api.generator.runtime.schema.SchemaRegistry;
import com.api.generator.runtime.schema.SqlTypeMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Generic data access via {@link NamedParameterJdbcTemplate}.
 * All queries are built by {@link SqlBuilder} with whitelist validation.
 */
public class DynamicRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SqlBuilder sqlBuilder;
    private final SchemaRegistry registry;

    public DynamicRepository(NamedParameterJdbcTemplate jdbc, SqlBuilder sqlBuilder, SchemaRegistry registry) {
        this.jdbc = jdbc;
        this.sqlBuilder = sqlBuilder;
        this.registry = registry;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAll(String table, int offset, int limit,
                                              String sortCol, String sortDir,
                                              Map<String, Object> filters) {
        TableInfo info = requireTable(table);
        List<String> filterCols = filters == null ? List.of() : new ArrayList<>(filters.keySet());

        String sql = sqlBuilder.selectAll(info, filterCols, sortCol, sortDir);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("limit", limit);
        params.addValue("offset", offset);
        if (filters != null) {
            for (Map.Entry<String, Object> e : filters.entrySet()) {
                ColumnInfo col = findColumn(info, e.getKey());
                params.addValue(e.getKey(), SqlTypeMapper.convertValue(col, e.getValue()));
            }
        }

        return jdbc.queryForList(sql, params);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findById(String table, Map<String, Object> pkValues) {
        TableInfo info = requireTable(table);
        String sql = sqlBuilder.selectByPk(info);
        MapSqlParameterSource params = buildPkParams(info, pkValues);

        List<Map<String, Object>> results = jdbc.queryForList(sql, params);
        if (results.isEmpty()) {
            throw new NotFoundException(table + " with id " + pkValues + " not found");
        }
        return results.get(0);
    }

    @Transactional
    public Map<String, Object> insert(String table, Map<String, Object> values) {
        TableInfo info = requireTable(table);
        List<String> cols = new ArrayList<>(values.keySet());

        String sql = sqlBuilder.insert(info, cols);
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (String col : cols) {
            ColumnInfo ci = findColumn(info, col);
            params.addValue(col, SqlTypeMapper.convertValue(ci, values.get(col)));
        }

        // Try to get generated keys for auto-increment PKs
        boolean hasAutoIncrementPk = info.getPrimaryKeys().stream()
                .anyMatch(pk -> info.getColumns().stream()
                        .filter(c -> c.getName().equalsIgnoreCase(pk))
                        .anyMatch(ColumnInfo::isAutoIncrement));

        if (hasAutoIncrementPk) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(sql, params, keyHolder, info.getPrimaryKeys().toArray(new String[0]));
            Map<String, Object> keys = keyHolder.getKeys();
            if (keys != null && !keys.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>(values);
                result.putAll(keys);
                return result;
            }
        } else {
            jdbc.update(sql, params);
        }

        // Return the inserted row by PK if possible
        try {
            Map<String, Object> pkVals = new LinkedHashMap<>();
            for (String pk : info.getPrimaryKeys()) {
                Object v = values.get(pk);
                if (v == null) return values; // can't look up without PK
                pkVals.put(pk, v);
            }
            return findById(table, pkVals);
        } catch (Exception e) {
            return values;
        }
    }

    @Transactional
    public Map<String, Object> update(String table, Map<String, Object> pkValues, Map<String, Object> values) {
        TableInfo info = requireTable(table);
        List<String> cols = new ArrayList<>(values.keySet());

        String sql = sqlBuilder.updateByPk(info, cols);
        MapSqlParameterSource params = buildPkParams(info, pkValues);
        for (String col : cols) {
            ColumnInfo ci = findColumn(info, col);
            params.addValue(col, SqlTypeMapper.convertValue(ci, values.get(col)));
        }

        int updated = jdbc.update(sql, params);
        if (updated == 0) {
            throw new NotFoundException(table + " with id " + pkValues + " not found");
        }
        return findById(table, pkValues);
    }

    @Transactional
    public void delete(String table, Map<String, Object> pkValues) {
        TableInfo info = requireTable(table);
        String sql = sqlBuilder.deleteByPk(info);
        MapSqlParameterSource params = buildPkParams(info, pkValues);

        int affected = jdbc.update(sql, params);
        if (affected == 0) {
            throw new NotFoundException(table + " with id " + pkValues + " not found");
        }
    }

    @Transactional(readOnly = true)
    public long count(String table, Map<String, Object> filters) {
        TableInfo info = requireTable(table);
        List<String> filterCols = filters == null ? List.of() : new ArrayList<>(filters.keySet());
        String sql = sqlBuilder.count(info, filterCols);

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filters != null) {
            for (Map.Entry<String, Object> e : filters.entrySet()) {
                ColumnInfo col = findColumn(info, e.getKey());
                params.addValue(e.getKey(), SqlTypeMapper.convertValue(col, e.getValue()));
            }
        }

        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result != null ? result : 0L;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TableInfo requireTable(String table) {
        return registry.getTable(table)
                .orElseThrow(() -> new NotFoundException("Table '" + table + "' not found in schema"));
    }

    private ColumnInfo findColumn(TableInfo table, String colName) {
        return table.getColumns().stream()
                .filter(c -> c.getName().equalsIgnoreCase(colName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown column '" + colName + "' in table '" + table.getName() + "'"));
    }

    private MapSqlParameterSource buildPkParams(TableInfo info, Map<String, Object> pkValues) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        for (String pk : info.getPrimaryKeys()) {
            Object val = pkValues.get(pk);
            if (val == null) {
                throw new IllegalArgumentException("Missing primary key value for column '" + pk + "'");
            }
            ColumnInfo ci = findColumn(info, pk);
            params.addValue("pk_" + pk, SqlTypeMapper.convertValue(ci, val));
        }
        return params;
    }
}
