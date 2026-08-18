package com.api.generator.runtime.web;

import com.api.generator.runtime.config.RuntimeSecurityProperties;
import com.api.generator.runtime.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthControllerTest {

    @Test
    void returnsJwtForValidBootstrapCredentials() {
        RuntimeSecurityProperties properties = properties();
        JwtTokenProvider tokens = new JwtTokenProvider(properties);
        AuthController controller = new AuthController(tokens, new BCryptPasswordEncoder(), properties);

        var response = controller.login(new AuthController.LoginRequest("admin", "Password123!"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("admin", tokens.validateAndGetSubject(response.getBody().token()));
    }

    @Test
    void rejectsInvalidCredentialsAndMissingHash() {
        RuntimeSecurityProperties properties = properties();
        AuthController controller = new AuthController(new JwtTokenProvider(properties), new BCryptPasswordEncoder(), properties);

        assertEquals(HttpStatus.UNAUTHORIZED, controller.login(new AuthController.LoginRequest("other", "Password123!")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, controller.login(new AuthController.LoginRequest("admin", "bad")).getStatusCode());

        properties.getBootstrap().setPassword("plain-text");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller.login(new AuthController.LoginRequest("admin", "Password123!")).getStatusCode());
    }

    private static RuntimeSecurityProperties properties() {
        RuntimeSecurityProperties properties = new RuntimeSecurityProperties();
        properties.getBootstrap().setUsername("admin");
        properties.getBootstrap().setPassword(new BCryptPasswordEncoder().encode("Password123!"));
        properties.getJwt().setSecret("01234567890123456789012345678901");
        properties.getJwt().setIssuer("generated-api");
        properties.getJwt().setExpirationSeconds(3600);
        return properties;
    }
}
