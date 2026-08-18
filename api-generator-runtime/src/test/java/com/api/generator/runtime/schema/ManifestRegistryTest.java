package com.api.generator.runtime.schema;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestRegistryTest {

    @Test
    void deniesOperationsMissingFromTableManifest() {
        TableManifest tableManifest = new TableManifest();
        tableManifest.setOperations(EnumSet.of(Operation.LIST, Operation.GET_BY_ID));
        ApiManifest manifest = new ApiManifest();
        manifest.setTables(Map.of("books", tableManifest));
        ManifestRegistry registry = new ManifestRegistry();
        registry.register(manifest);

        assertFalse(registry.isDenied("books", Operation.LIST));
        assertTrue(registry.isDenied("books", Operation.CREATE));
        assertTrue(registry.operationsFor("books").contains(Operation.GET_BY_ID));
    }

    @Test
    void allowsEverythingWhenTableIsMissingFromManifest() {
        ManifestRegistry registry = new ManifestRegistry();

        assertFalse(registry.isDenied("authors", Operation.DELETE));
        assertTrue(registry.operationsFor("authors").containsAll(Operation.all()));
    }
}
