package com.api.generator.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        String login,
        String username,
        String email,
        @NotBlank(message = "password is required") String password
) {
    public String resolvedLogin() {
        if (login != null && !login.isBlank()) return login.trim();
        if (username != null && !username.isBlank()) return username.trim();
        if (email != null && !email.isBlank()) return email.trim();
        return null;
    }
}
