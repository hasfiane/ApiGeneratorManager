package com.api.generator.runtime.autoconfigure;

import com.api.generator.runtime.config.RuntimeDbProperties;
import com.api.generator.runtime.config.RuntimeSecurityProperties;
import com.api.generator.runtime.config.RuntimeTableProperties;
import com.api.generator.runtime.jdbc.DynamicRepository;
import com.api.generator.runtime.jdbc.SqlBuilder;
import com.api.generator.runtime.schema.ManifestBootstrap;
import com.api.generator.runtime.schema.ManifestRegistry;
import com.api.generator.runtime.schema.SchemaBootstrap;
import com.api.generator.runtime.schema.SchemaHintsApplier;
import com.api.generator.runtime.schema.SchemaRegistry;
import com.api.generator.runtime.security.JwtAuthenticationFilter;
import com.api.generator.runtime.security.JwtTokenProvider;
import com.api.generator.runtime.security.NoSecurityConfig;
import com.api.generator.runtime.security.SecurityConfig;
import com.api.generator.runtime.validation.SchemaValidator;
import com.api.generator.runtime.web.AuthController;
import com.api.generator.runtime.web.DynamicCrudController;
import com.api.generator.runtime.web.DynamicOpenApiContributor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@AutoConfiguration
@ConditionalOnProperty(prefix = "generator.runtime", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({RuntimeSecurityProperties.class, RuntimeDbProperties.class, RuntimeTableProperties.class})
@Import({SecurityConfig.class, NoSecurityConfig.class, AuthController.class})
public class RuntimeApiAutoConfiguration {

    // ── ObjectMapper fallback ─────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(RuntimeSecurityProperties securityProperties) {
        return new JwtTokenProvider(securityProperties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "jwtAuthenticationFilter")
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokens) {
        return new JwtAuthenticationFilter(tokens);
    }

    @Bean
    public SchemaRegistry schemaRegistry() {
        return new SchemaRegistry();
    }

    @Bean
    public SchemaBootstrap schemaBootstrap(SchemaRegistry registry, ObjectMapper objectMapper) {
        return new SchemaBootstrap(registry, objectMapper);
    }

    @Bean
    public SchemaHintsApplier schemaHintsApplier(SchemaRegistry registry,
                                                  RuntimeTableProperties tableProperties) {
        return new SchemaHintsApplier(registry, tableProperties);
    }

    // ── Manifest ──────────────────────────────────────────────────────────────

    @Bean
    public ManifestRegistry manifestRegistry() {
        return new ManifestRegistry();
    }

    @Bean
    public ManifestBootstrap manifestBootstrap(ManifestRegistry registry, ObjectMapper objectMapper) {
        return new ManifestBootstrap(registry, objectMapper);
    }

    // ── OpenAPI ───────────────────────────────────────────────────────────────

    @Bean
    public DynamicOpenApiContributor dynamicOpenApiContributor(SchemaRegistry schemaRegistry,
                                                               ManifestRegistry manifestRegistry) {
        return new DynamicOpenApiContributor(schemaRegistry, manifestRegistry);
    }

    // ── Dynamic CRUD ─────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(prefix = "generator.db", name = "url")
    public SqlBuilder sqlBuilder(RuntimeDbProperties props) {
        return new SqlBuilder(props.getType());
    }

    @Bean
    @ConditionalOnProperty(prefix = "generator.db", name = "url")
    public DynamicRepository dynamicRepository(NamedParameterJdbcTemplate jdbc,
                                               SqlBuilder sqlBuilder, SchemaRegistry registry) {
        return new DynamicRepository(jdbc, sqlBuilder, registry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "generator.db", name = "url")
    public SchemaValidator schemaValidator(SchemaRegistry registry) {
        return new SchemaValidator(registry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "generator.db", name = "url")
    public DynamicCrudController dynamicCrudController(DynamicRepository repository,
                                                       SchemaRegistry schemaRegistry,
                                                       SchemaValidator validator,
                                                       ManifestRegistry manifestRegistry) {
        return new DynamicCrudController(repository, schemaRegistry, validator, manifestRegistry);
    }
}
