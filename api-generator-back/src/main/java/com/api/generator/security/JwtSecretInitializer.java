package com.api.generator.security;

import com.api.generator.auth.JwtProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtSecretInitializer {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretInitializer.class);

    private final JwtProperties jwtProperties;
    private final SecretValidator secretValidator;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public JwtSecretInitializer(JwtProperties jwtProperties, SecretValidator secretValidator) {
        this.jwtProperties = jwtProperties;
        this.secretValidator = secretValidator;
    }

    @PostConstruct
    public void validateSecret() {
        String currentSecret = jwtProperties.secret();
        if (secretValidator.isDefaultSecret(currentSecret)) {
            if (isProductionProfile()) {
                throw new SecurityException(
                        "CRITICAL SECURITY ERROR: MANAGER_JWT_SECRET is missing, weak, or set to a development default. " +
                        "Set MANAGER_JWT_SECRET to a random value of at least 32 characters before starting prod."
                );
            }
            log.warn("Default JWT secret detected. This is allowed only for local development.");
            return;
        }

        try {
            secretValidator.validateJwtSecret(currentSecret);
            log.info("JWT secret validated successfully.");
        } catch (SecurityException e) {
            if (isProductionProfile()) {
                throw new SecurityException("JWT secret validation failed: " + e.getMessage(), e);
            }
            log.error("JWT secret validation failed: {}", e.getMessage());
        }
    }

    private boolean isProductionProfile() {
        return activeProfile != null &&
               (activeProfile.contains("prod") || activeProfile.contains("production"));
    }
}
