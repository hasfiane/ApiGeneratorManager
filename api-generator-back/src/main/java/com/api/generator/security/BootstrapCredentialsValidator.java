package com.api.generator.security;

import com.api.generator.config.AccountProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapCredentialsValidator {

    private final AccountProperties accountProperties;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public BootstrapCredentialsValidator(AccountProperties accountProperties) {
        this.accountProperties = accountProperties;
    }

    @PostConstruct
    public void validate() {
        if (!isProductionProfile() || !accountProperties.isBootstrapEnabled()) {
            return;
        }

        String password = accountProperties.getBootstrapPassword();
        if (isWeakBootstrapPassword(password)) {
            throw new SecurityException("Bootstrap admin password is too weak for production.");
        }
    }

    private boolean isWeakBootstrapPassword(String password) {
        if (password == null || password.length() < 16) {
            return true;
        }
        String normalized = password.toLowerCase();
        return normalized.contains("admin")
                || normalized.contains("replace_with")
                || normalized.contains("change_me")
                || normalized.contains("changeme")
                || normalized.contains("dev-only");
    }

    private boolean isProductionProfile() {
        return activeProfile != null &&
                (activeProfile.contains("prod") || activeProfile.contains("production"));
    }
}
