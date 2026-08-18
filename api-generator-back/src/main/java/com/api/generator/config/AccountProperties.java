package com.api.generator.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.account")
public class AccountProperties {
    @NotBlank
    private String defaultPlan = "FREE";

    @NotBlank
    private String legacyPlan = "PRO";

    @NotBlank
    private String defaultRoles = "ROLE_USER";

    private boolean bootstrapEnabled = false;
    private boolean bootstrapResetPasswordOnStartup = false;
    private boolean adminUnlimitedQuotas = false;

    @NotBlank
    private String bootstrapEmail = "admin@localhost";

    private String bootstrapPassword = "";

    @NotBlank
    private String bootstrapRoles = "ROLE_ADMIN,ROLE_USER";

    @NotBlank
    private String bootstrapPlan = "PRO";

    public String getDefaultPlan() { return defaultPlan; }
    public void setDefaultPlan(String defaultPlan) { this.defaultPlan = defaultPlan; }

    public String getLegacyPlan() { return legacyPlan; }
    public void setLegacyPlan(String legacyPlan) { this.legacyPlan = legacyPlan; }

    public String getDefaultRoles() { return defaultRoles; }
    public void setDefaultRoles(String defaultRoles) { this.defaultRoles = defaultRoles; }

    public boolean isBootstrapEnabled() { return bootstrapEnabled; }
    public void setBootstrapEnabled(boolean bootstrapEnabled) { this.bootstrapEnabled = bootstrapEnabled; }

    public boolean isBootstrapResetPasswordOnStartup() { return bootstrapResetPasswordOnStartup; }
    public void setBootstrapResetPasswordOnStartup(boolean bootstrapResetPasswordOnStartup) { this.bootstrapResetPasswordOnStartup = bootstrapResetPasswordOnStartup; }

    public boolean isAdminUnlimitedQuotas() { return adminUnlimitedQuotas; }
    public void setAdminUnlimitedQuotas(boolean adminUnlimitedQuotas) { this.adminUnlimitedQuotas = adminUnlimitedQuotas; }

    public String getBootstrapEmail() { return bootstrapEmail; }
    public void setBootstrapEmail(String bootstrapEmail) { this.bootstrapEmail = bootstrapEmail; }

    public String getBootstrapPassword() { return bootstrapPassword; }
    public void setBootstrapPassword(String bootstrapPassword) { this.bootstrapPassword = bootstrapPassword; }

    public String getBootstrapRoles() { return bootstrapRoles; }
    public void setBootstrapRoles(String bootstrapRoles) { this.bootstrapRoles = bootstrapRoles; }

    public String getBootstrapPlan() { return bootstrapPlan; }
    public void setBootstrapPlan(String bootstrapPlan) { this.bootstrapPlan = bootstrapPlan; }
}
