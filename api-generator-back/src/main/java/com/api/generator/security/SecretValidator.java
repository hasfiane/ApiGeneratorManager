package com.api.generator.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


/**
 * Validates and generates cryptographically secure secrets.
 */
@Component
public class SecretValidator {

    private static final Logger log = LoggerFactory.getLogger(SecretValidator.class);

    private static final int MIN_SECRET_LENGTH = 32; // 256 bits minimum
    private static final int RECOMMENDED_SECRET_LENGTH = 64; // 512 bits recommended

    private static final String[] WEAK_SECRETS = {
        "CHANGE_ME",
        "secret",
        "password",
        "admin",
        "test",
        "demo",
        "changeme",
        "replace",
        "12345",
        "qwerty"
    };

    /**
     * Validates a JWT secret for security.
     */
    public void validateJwtSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new SecurityException("JWT secret cannot be empty");
        }

        // Check minimum length
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new SecurityException(
                "JWT secret too short. Minimum " + MIN_SECRET_LENGTH + " characters required for security."
            );
        }

        // Check for weak/default secrets
        String lowerSecret = secret.toLowerCase();
        for (String weak : WEAK_SECRETS) {
            if (lowerSecret.contains(weak.toLowerCase())) {
                throw new SecurityException(
                    "JWT secret contains weak/default value: '" + weak + "'. Please use a strong random secret."
                );
            }
        }

        // Warn if not recommended length
        if (secret.length() < RECOMMENDED_SECRET_LENGTH) {
            log.warn("JWT secret is shorter than recommended {} characters. Current length: {}",
                RECOMMENDED_SECRET_LENGTH, secret.length());
        }
    }

    /**
     * Checks if a secret is the default weak value.
     */
    public boolean isDefaultSecret(String secret) {
        if (secret == null) return true;
        String lower = secret.toLowerCase();
        for (String weak : WEAK_SECRETS) {
            if (lower.contains(weak.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
