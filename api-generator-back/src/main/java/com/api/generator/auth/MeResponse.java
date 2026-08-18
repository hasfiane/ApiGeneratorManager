package com.api.generator.auth;

import java.util.List;

public record MeResponse(
        String email,
        String plan,
        List<String> roles,
        Quotas quotas
) {}
