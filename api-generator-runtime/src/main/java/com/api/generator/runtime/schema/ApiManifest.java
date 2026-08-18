package com.api.generator.runtime.schema;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class ApiManifest {
    private Map<String, TableManifest> tables = new LinkedHashMap<>();

    public boolean isDenied(String table, Operation op) {
        TableManifest tm = tables.get(table.toLowerCase(Locale.ROOT));
        return tm != null && !tm.allows(op);
    }

    public Set<Operation> operationsFor(String table) {
        TableManifest tm = tables.get(table.toLowerCase(Locale.ROOT));
        return tm != null ? tm.getOperations() : Operation.all();
    }
}
