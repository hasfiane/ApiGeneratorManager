package com.api.generator.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt.cookie")
public record JwtCookieProperties(
        String name,
        boolean secure,
        boolean httpOnly,
        String sameSite,
        String path,
        String domain
) {
}
