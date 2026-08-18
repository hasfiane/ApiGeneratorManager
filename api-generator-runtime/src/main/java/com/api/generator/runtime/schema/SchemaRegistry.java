package com.api.generator.runtime.schema;

import com.api.generator.schema.TableInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of the database schema, populated at startup by {@link SchemaBootstrap}.
 */
public class SchemaRegistry {

    private final Map<String, TableInfo> tables = new ConcurrentHashMap<>();

    public void register(List<TableInfo> tableList) {
        tables.clear();
        for (TableInfo t : tableList) {
            tables.put(t.getName().toLowerCase(Locale.ROOT), t);
        }
    }

    public Optional<TableInfo> getTable(String name) {
        return Optional.ofNullable(tables.get(name.toLowerCase(Locale.ROOT)));
    }

    public Set<String> getAllTableNames() {
        return Collections.unmodifiableSet(tables.keySet());
    }

    public Collection<TableInfo> getAllTables() {
        return Collections.unmodifiableCollection(tables.values());
    }

    public boolean isEmpty() {
        return tables.isEmpty();
    }
}
