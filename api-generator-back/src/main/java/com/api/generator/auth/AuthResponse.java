package com.api.generator.auth;

import java.util.List;

public record AuthResponse(
        String email,
        String plan,
        List<String> roles
) {}
