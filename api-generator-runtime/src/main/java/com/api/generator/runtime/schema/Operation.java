package com.api.generator.runtime.schema;

import java.util.EnumSet;
import java.util.Set;

/** CRUD operations that can be enabled/disabled per table in api-manifest.json. */
public enum Operation {
    LIST, GET_BY_ID, CREATE, UPDATE, DELETE;
    public static Set<Operation> all() { return EnumSet.allOf(Operation.class); }
}
