package com.api.generator.runtime.security;

import com.api.generator.runtime.config.RuntimeSecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    @Test
    void generatesAndValidatesTokenSubject() {
        JwtTokenProvider provider = new JwtTokenProvider(properties("01234567890123456789012345678901", "generated-api"));

        String token = provider.generate("admin");

        assertEquals("admin", provider.validateAndGetSubject(token));
    }

    @Test
    void acceptsBase64Secret() {
        String secret = Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());
        RuntimeSecurityProperties props = properties(null, "generated-api");
        props.getJwt().setSecretBase64(secret);
        JwtTokenProvider provider = new JwtTokenProvider(props);

        assertEquals("user", provider.validateAndGetSubject(provider.generate("user")));
    }

    @Test
    void includesAudienceRolesJtiAndKeyId() {
        JwtTokenProvider provider = new JwtTokenProvider(properties("01234567890123456789012345678901", "generated-api"));

        String token = provider.generate("operator", List.of("ADMIN", "AUDITOR"));
        String[] segments = token.split("\\.");
        String header = new String(Base64.getUrlDecoder().decode(segments[0]));
        String claims = new String(Base64.getUrlDecoder().decode(segments[1]));

        assertEquals(true, claims.contains("\"aud\":[\"generated-api\"]"));
        assertEquals(true, claims.contains("\"sub\":\"operator\""));
        assertEquals(List.of("ADMIN", "AUDITOR"), provider.validateAndGetRoles(token));
        assertEquals(true, claims.contains("\"jti\":\""));
        assertEquals(true, header.contains("\"kid\":\"hmac-"));
    }

    @Test
    void rejectsMissingSecretAndIssuer() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(properties("short", "generated-api")));
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(properties("01234567890123456789012345678901", "")));
    }

    private static RuntimeSecurityProperties properties(String secret, String issuer) {
        RuntimeSecurityProperties props = new RuntimeSecurityProperties();
        props.getJwt().setSecret(secret);
        props.getJwt().setIssuer(issuer);
        props.getJwt().setExpirationSeconds(30);
        return props;
    }
}
