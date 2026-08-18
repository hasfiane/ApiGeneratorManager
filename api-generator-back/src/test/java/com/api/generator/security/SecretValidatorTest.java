package com.api.generator.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretValidatorTest {

    private final SecretValidator validator = new SecretValidator();

    @Test
    void rejectsBlankShortAndDefaultJwtSecrets() {
        assertThrows(SecurityException.class, () -> validator.validateJwtSecret(""));
        assertThrows(SecurityException.class, () -> validator.validateJwtSecret("short"));
        assertThrows(SecurityException.class,
                () -> validator.validateJwtSecret("change_me_value_that_is_long_enough_to_fail_default_check"));
    }

    @Test
    void acceptsStrongJwtSecret() {
        assertDoesNotThrow(() -> validator.validateJwtSecret(
                "4UmYB9qZHbEQzAzsPg6AtJtw6LbJ6pYnfFECX2rxuhr2mE2b3ddZPZk5j7qP6gTk"
        ));
    }

    @Test
    void detectsDefaultSecretsForStartupGuards() {
        assertTrue(validator.isDefaultSecret(null));
        assertTrue(validator.isDefaultSecret("dev-only-jwt-secret-change-me-32chars-min"));
        assertFalse(validator.isDefaultSecret(
                "4UmYB9qZHbEQzAzsPg6AtJtw6LbJ6pYnfFECX2rxuhr2mE2b3ddZPZk5j7qP6gTk"
        ));
    }
}
