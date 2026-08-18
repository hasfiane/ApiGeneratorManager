package com.api.generator.runtime.schema;

import java.util.Set;

/**
 * In-memory holder of the API manifest, populated at startup by {@link ManifestBootstrap}.
 * Thread-safe via volatile reference swap.
 */
public class ManifestRegistry {

    private volatile ApiManifest manifest = new ApiManifest();

    public void register(ApiManifest m) {
        this.manifest = m;
    }

    public boolean isDenied(String table, Operation op) {
        return manifest.isDenied(table, op);
    }

    public Set<Operation> operationsFor(String table) {
        return manifest.operationsFor(table);
    }
}
