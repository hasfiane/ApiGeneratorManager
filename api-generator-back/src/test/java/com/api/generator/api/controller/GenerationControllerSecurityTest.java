package com.api.generator.api.controller;

import com.api.generator.account.repo.AppUserRepository;
import com.api.generator.account.service.AccountService;
import com.api.generator.account.service.ApiProjectService;
import com.api.generator.account.service.PlanCapabilityService;
import com.api.generator.api.service.GenerationJobService;
import com.api.generator.api.service.GenerationService;
import com.api.generator.api.service.PreviewConfigCodec;
import com.api.generator.api.service.RuntimeAccessUrlService;
import com.api.generator.config.GeneratorProperties;
import com.api.generator.schema.DatabaseType;
import com.api.generator.security.ClientIpResolver;
import com.api.generator.security.InputSanitizer;
import com.api.generator.security.JdbcUrlValidator;
import com.api.generator.security.RequestRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GenerationControllerSecurityTest {

    @Test
    void uploadedYamlConfigCannotOverrideUnsafeGenerationDefaults() {
        GeneratorProperties baseProps = new GeneratorProperties();
        baseProps.getSecurity().setEnabled(true);
        baseProps.getSecurity().setBootstrapPassword("base-admin-password");
        baseProps.getSecurity().setJwtSecret("base-generated-api-jwt-secret-32chars");
        baseProps.getMaven().setGroupId("com.safe");
        baseProps.getMaven().setArtifactId("safe-api");

        GenerationController controller = new GenerationController(
                mock(GenerationJobService.class),
                mock(GenerationService.class),
                mock(PreviewConfigCodec.class),
                baseProps,
                new JdbcUrlValidator(true, ""),
                new InputSanitizer(),
                mock(ClientIpResolver.class),
                mock(RequestRateLimiter.class),
                mock(AppUserRepository.class),
                mock(ApiProjectService.class),
                mock(AccountService.class),
                mock(PlanCapabilityService.class),
                mock(RuntimeAccessUrlService.class)
        );

        GeneratorProperties uploaded = new GeneratorProperties();
        uploaded.setAppName(" unsafe name ");
        uploaded.setBasePackage("com.example.uploaded");
        uploaded.setOutputDir("/tmp/attacker-controlled");
        uploaded.setCleanOutputDir(false);
        uploaded.getMaven().setGroupId("evil.group");
        uploaded.getMaven().setArtifactId("evil-artifact");
        uploaded.getSecurity().setEnabled(false);
        uploaded.getSecurity().setJwtSecret("attacker-generated-api-jwt-secret-32chars");
        uploaded.getDb().setType(DatabaseType.POSTGRESQL);
        uploaded.getDb().setUrl("jdbc:postgresql://db.example.invalid:5432/app");
        uploaded.getDb().setUsername(" db_user ");
        uploaded.getDb().setPassword(" db_password ");
        uploaded.getDb().setSchema("public");
        uploaded.getDb().setProperties(new LinkedHashMap<>());
        uploaded.getDb().getProperties().put("socketFactory", "evil.Factory");

        GeneratorProperties sanitized = ReflectionTestUtils.invokeMethod(controller, "sanitizeUploadedConfig", uploaded);

        assertNotNull(sanitized);
        assertEquals("unsafe_name", sanitized.getAppName());
        assertEquals("com.example.uploaded", sanitized.getBasePackage());
        assertEquals(baseProps.getOutputDir(), sanitized.getOutputDir());
        assertTrue(sanitized.isCleanOutputDir());
        assertEquals("com.safe", sanitized.getMaven().getGroupId());
        assertEquals("safe-api", sanitized.getMaven().getArtifactId());
        assertTrue(sanitized.getSecurity().isEnabled());
        assertEquals("base-generated-api-jwt-secret-32chars", sanitized.getSecurity().getJwtSecret());
        assertEquals("db_user", sanitized.getDb().getUsername());
        assertEquals("db_password", sanitized.getDb().getPassword());
        assertTrue(sanitized.getDb().getProperties().isEmpty());
    }
}
