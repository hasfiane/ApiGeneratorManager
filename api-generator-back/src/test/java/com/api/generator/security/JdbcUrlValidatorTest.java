    package com.api.generator.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcUrlValidatorTest {

    @Test
    void blocksPrivateJdbcHostWhenPrivateHostsAreDisabled() {
        JdbcUrlValidator validator = new JdbcUrlValidator(false, "");

        assertThrows(SecurityException.class,
                () -> validator.validate("jdbc:postgresql://127.0.0.1:5432/app"));
    }

    @Test
    void allowsExplicitlyAllowedPrivateJdbcHost() {
        JdbcUrlValidator validator = new JdbcUrlValidator(false, "127.0.0.1");

        assertDoesNotThrow(() -> validator.validate("jdbc:postgresql://127.0.0.1:5432/app"));
    }

    @Test
    void blocksH2TcpPrivateHostWhenPrivateHostsAreDisabled() {
        JdbcUrlValidator validator = new JdbcUrlValidator(false, "");

        assertThrows(SecurityException.class,
                () -> validator.validate("jdbc:h2:tcp://127.0.0.1/mem:app"));
    }

    @Test
    void rejectsInvalidIpv4Literal() {
        JdbcUrlValidator validator = new JdbcUrlValidator(true, "");

        assertThrows(SecurityException.class,
                () -> validator.validate("jdbc:mysql://999.999.999.999:3306/app"));
    }

    @Test
    void rejectsBlankPort() {
        JdbcUrlValidator validator = new JdbcUrlValidator(true, "");

        assertThrows(SecurityException.class,
                () -> validator.validate("jdbc:mysql://localhost:/app"));
    }

    @Test
    void rejectsMalformedBracketedIpv6Host() {
        JdbcUrlValidator validator = new JdbcUrlValidator(true, "");

        assertThrows(SecurityException.class,
                () -> validator.validate("jdbc:postgresql://[::1]broken:5432/app"));
    }

    @Test
    void rejectsH2InitScripts() {
        JdbcUrlValidator validator = new JdbcUrlValidator(true, "");

        assertThrows(SecurityException.class,
                () -> validator.validate("jdbc:h2:mem:generated;INIT=RUNSCRIPT FROM 'https://example.test/schema.sql'"));
    }

    @Test
    void allowsH2DemoMemorySettings() {
        JdbcUrlValidator validator = new JdbcUrlValidator(true, "");

        assertDoesNotThrow(() -> validator.validate(
                "jdbc:h2:mem:generated_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"));
    }

    @Test
    void allowsPublicIpWhenPrivateHostsAreDisabled() {
        JdbcUrlValidator validator = new JdbcUrlValidator(false, "");

        assertDoesNotThrow(() -> validator.validate("jdbc:postgresql://8.8.8.8:5432/app"));
    }

    @Test
    void allowsUnresolvedHostnameWhenItIsNotExplicitlyPrivate() {
        JdbcUrlValidator validator = new JdbcUrlValidator(false, "");

        assertDoesNotThrow(() -> validator.validate("jdbc:postgresql://db.example.invalid:5432/app"));
    }
}
