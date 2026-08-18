package com.api.generator.runtime.schema;


import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Loads {@code api-manifest.json} from the classpath into {@link ManifestRegistry} at startup.
 * Runs via {@link PostConstruct} — before Tomcat accepts connections — so the manifest
 * is always ready when the first OpenAPI or CRUD request arrives.
 * If the file is missing, all operations are allowed for all tables (safe default).
 */
public class ManifestBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ManifestBootstrap.class);
    private static final String MANIFEST_RESOURCE = "/api-manifest.json";

    private final ManifestRegistry registry;
    private final ObjectMapper objectMapper;

    public ManifestBootstrap(ManifestRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        InputStream is = getClass().getResourceAsStream(MANIFEST_RESOURCE);
        if (is == null) {
            log.info("api-manifest.json not found — all operations enabled for all tables.");
            return;
        }
        try {
            ApiManifest manifest = objectMapper.readValue(is, ApiManifest.class);
            registry.register(manifest);
            log.info("API manifest loaded: {} table(s) configured.", manifest.getTables().size());
        } catch (Exception e) {
            log.error("Failed to parse api-manifest.json: {} — falling back to all-operations mode.", e.getMessage());
        }
    }
}
