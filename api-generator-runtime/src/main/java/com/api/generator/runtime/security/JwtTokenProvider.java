package com.api.generator.runtime.security;

import com.api.generator.runtime.config.RuntimeSecurityProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.security.MessageDigest;

public class JwtTokenProvider {

    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final String keyId;
    private final long expirationSeconds;

    public JwtTokenProvider(RuntimeSecurityProperties securityProperties) {
        RuntimeSecurityProperties.Jwt jwt = securityProperties.getJwt();

        byte[] keyBytes = null;
        if (jwt.getSecretBase64() != null && !jwt.getSecretBase64().isBlank()) {
            keyBytes = Base64.getDecoder().decode(jwt.getSecretBase64());
        } else if (jwt.getSecret() != null && !jwt.getSecret().isBlank()) {
            keyBytes = jwt.getSecret().getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes == null || keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be configured and at least 32 bytes.");
        }
        if (jwt.getIssuer() == null || jwt.getIssuer().isBlank()) {
            throw new IllegalStateException("JWT issuer must be configured.");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = jwt.getIssuer();
        this.audience = jwt.getAudience() == null || jwt.getAudience().isBlank()
                ? this.issuer : jwt.getAudience();
        this.keyId = keyId(keyBytes);
        this.expirationSeconds = Math.max(60, jwt.getExpirationSeconds());
    }

    public String generate(String username) {
        return generate(username, List.of("USER"));
    }

    public String generate(String username, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim("roles", roles == null ? List.of() : roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .header().keyId(keyId).and()
                .signWith(key)
                .compact();
    }

    public String validateAndGetSubject(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> validateAndGetRoles(String token) {
        Object roles = Jwts.parser().verifyWith(key).requireIssuer(issuer).requireAudience(audience)
                .build().parseSignedClaims(token).getPayload().get("roles");
        if (!(roles instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static String keyId(byte[] keyBytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            StringBuilder value = new StringBuilder("hmac-");
            for (int index = 0; index < 6; index++) value.append(String.format("%02x", digest[index]));
            return value.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive JWT key id", exception);
        }
    }
}
