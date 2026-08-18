package com.api.generator.runtime.schema;

import com.api.generator.schema.TableInfo;
import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

/**
 * Loads the pre-generated schema from the embedded {@code schema.json} classpath resource
 * into {@link SchemaRegistry} at startup.
 *
 * Uses {@link PostConstruct} — not {@code ApplicationRunner} — so the registry is populated
 * before Tomcat accepts connections. This prevents SpringDoc from caching an empty spec
 * on the first request.
 */
public class SchemaBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SchemaBootstrap.class);
    private static final String SCHEMA_RESOURCE = "/schema.json";

    private final SchemaRegistry registry;
    private final ObjectMapper objectMapper;

    public SchemaBootstrap(SchemaRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        InputStream is = getClass().getResourceAsStream(SCHEMA_RESOURCE);
        if (is == null) {
            log.error("schema.json not found on classpath — dynamic API endpoints will be unavailable.");
            return;
        }
        try {
            ObjectMapper schemaMapper = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            List<TableInfo> tables = schemaMapper.readValue(is, new TypeReference<>() {});
            if (tables.isEmpty()) {
                log.warn("schema.json is empty — no tables registered.");
                return;
            }
            registry.register(tables);
            log.info("Schema loaded: {} table(s) → {}", tables.size(), registry.getAllTableNames());
        } catch (Exception e) {
            log.error("Failed to parse schema.json: {} — dynamic endpoints unavailable.", e.getMessage());
        }
    }
}
