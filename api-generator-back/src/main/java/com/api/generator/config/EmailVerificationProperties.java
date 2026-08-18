package com.api.generator.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.account.email-verification")
public class EmailVerificationProperties {
    private boolean enabled = true;

    @Min(5)
    private long expirationMinutes = 30;

    @NotBlank
    private String frontendVerifyUrl = "http://localhost:5173/verify-email";

    @NotBlank
    private String resetPasswordFrontendUrl = "http://localhost:5173/reset-password";

    private String from = "no-reply@localhost";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getExpirationMinutes() { return expirationMinutes; }
    public void setExpirationMinutes(long expirationMinutes) { this.expirationMinutes = expirationMinutes; }

    public String getFrontendVerifyUrl() { return frontendVerifyUrl; }
    public void setFrontendVerifyUrl(String frontendVerifyUrl) { this.frontendVerifyUrl = frontendVerifyUrl; }

    public String getResetPasswordFrontendUrl() { return resetPasswordFrontendUrl; }
    public void setResetPasswordFrontendUrl(String resetPasswordFrontendUrl) { this.resetPasswordFrontendUrl = resetPasswordFrontendUrl; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
}
