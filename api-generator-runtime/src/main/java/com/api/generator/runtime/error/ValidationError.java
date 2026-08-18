package com.api.generator.runtime.error;

import java.time.Instant;
import java.util.List;

/**
 * Enhanced error response for validation errors with field-level details.
 */
public record ValidationError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(
            String field,
            Object rejectedValue,
            String message
    ) {}
}
