package com.api.generator.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigContractTest {

    @Test
    void unauthenticatedAuthFlowEndpointsStayPublic() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/api/generator/config/SecurityConfig.java"));

        assertTrue(source.contains("\"/api/auth/login\""));
        assertTrue(source.contains("\"/api/auth/register\""));
        assertTrue(source.contains("\"/api/auth/verify-email\""));
        assertTrue(source.contains("\"/api/auth/password-reset/request\""));
        assertTrue(source.contains("\"/api/auth/password-reset/confirm\""));
        assertTrue(source.contains("\"/api/auth/oauth2/status\""));
        assertTrue(source.contains("\"/oauth2/**\""));
        assertTrue(source.contains("\"/login/oauth2/**\""));
        assertTrue(source.contains("\"/actuator/health/readiness\""));
        assertTrue(source.contains("\"/actuator/health/liveness\""));
    }
}
