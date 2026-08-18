package com.api.generator.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.oauth2")
public class OAuth2Properties {
    @NotBlank
    private String successRedirect = "http://localhost:5173/oauth2/callback";

    public String getSuccessRedirect() { return successRedirect; }
    public void setSuccessRedirect(String successRedirect) { this.successRedirect = successRedirect; }
}
