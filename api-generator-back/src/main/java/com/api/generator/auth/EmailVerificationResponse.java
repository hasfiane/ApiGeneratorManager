package com.api.generator.auth;

public record EmailVerificationResponse(
        String message,
        String email
) {}
