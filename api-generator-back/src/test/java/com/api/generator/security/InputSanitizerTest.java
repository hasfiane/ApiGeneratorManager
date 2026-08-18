package com.api.generator.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputSanitizerTest {

    private final InputSanitizer sanitizer = new InputSanitizer();

    @Test
    void normalizesWhitespaceToUnderscore() {
        assertEquals("postgres_starter_api", sanitizer.normalizeAppName("postgres starter api"));
    }

    @Test
    void acceptsHyphenAndUnderscoreInAppName() {
        assertDoesNotThrow(() -> sanitizer.validateAppName("postgres-starter_api"));
    }

    @Test
    void rejectsAppNameThatDoesNotStartWithALetter() {
        assertThrows(SecurityException.class, () -> sanitizer.validateAppName("1postgres_api"));
    }
}
