package com.api.generator.admin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminDashboardControllerTest {

    @Test
    void redactsSecretsFromOperationalErrorMessages() throws Exception {
        Method method = AdminDashboardController.class.getDeclaredMethod("redactSensitive", String.class);
        method.setAccessible(true);

        String redacted = (String) method.invoke(
                null,
                "Connection failed password=s3cr3t token: abc123 db_password=\"plain\" Authorization: Bearer jwt-value"
        );

        assertEquals(
                "Connection failed password=*** token: *** db_password=\"***\" Authorization: Bearer ***",
                redacted
        );
    }
}
