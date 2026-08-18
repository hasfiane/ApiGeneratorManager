package com.api.generator.security;

import com.api.generator.config.AccountProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BootstrapCredentialsValidatorTest {

    @Test
    void rejectsWeakBootstrapPasswordWhenEnabledInProduction() {
        AccountProperties props = new AccountProperties();
        props.setBootstrapEnabled(true);
        props.setBootstrapPassword("dev-only-bootstrap-password-change-me");
        BootstrapCredentialsValidator validator = new BootstrapCredentialsValidator(props);
        ReflectionTestUtils.setField(validator, "activeProfile", "prod");

        assertThrows(SecurityException.class, validator::validate);
    }

    @Test
    void ignoresWeakBootstrapPasswordWhenBootstrapIsDisabledInProduction() {
        AccountProperties props = new AccountProperties();
        props.setBootstrapEnabled(false);
        props.setBootstrapPassword("dev-only-bootstrap-password-change-me");
        BootstrapCredentialsValidator validator = new BootstrapCredentialsValidator(props);
        ReflectionTestUtils.setField(validator, "activeProfile", "prod");

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void allowsBlankBootstrapPasswordWhenBootstrapIsDisabledInProduction() {
        AccountProperties props = new AccountProperties();
        props.setBootstrapEnabled(false);
        props.setBootstrapPassword("");
        BootstrapCredentialsValidator validator = new BootstrapCredentialsValidator(props);
        ReflectionTestUtils.setField(validator, "activeProfile", "prod");

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void allowsStrongBootstrapPasswordWhenEnabledInProduction() {
        AccountProperties props = new AccountProperties();
        props.setBootstrapEnabled(true);
        props.setBootstrapPassword("N5acn3SSE6JkQ7m3z6gBtFz8");
        BootstrapCredentialsValidator validator = new BootstrapCredentialsValidator(props);
        ReflectionTestUtils.setField(validator, "activeProfile", "prod");

        assertDoesNotThrow(validator::validate);
    }
}
