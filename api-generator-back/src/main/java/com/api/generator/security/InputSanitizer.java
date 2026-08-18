package com.api.generator.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Sanitizes and validates user inputs to prevent injection attacks.
 */
@Component
public class InputSanitizer {

    // Strict UUID pattern
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$"
    );

    // Safe app name (letters, digits, hyphen, underscore)
    private static final Pattern APP_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,31}$");

    // Safe package name
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile(
        "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$"
    );

    /**
     * Validates a job ID (must be a valid UUID).
     */
    public void validateJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new SecurityException("Job ID cannot be empty");
        }

        if (!UUID_PATTERN.matcher(jobId.toLowerCase()).matches()) {
            throw new SecurityException("Invalid job ID format. Must be a valid UUID.");
        }
    }

    public String normalizeAppName(String appName) {
        if (appName == null) {
            return null;
        }

        return appName.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-zA-Z0-9_-]", "_")
                .replaceAll("_+", "_");
    }

    /**
     * Validates an application name (letters, digits, hyphen, underscore, max 32 chars).
     */
    public void validateAppName(String appName) {
        if (appName == null || appName.isBlank()) {
            throw new SecurityException("Application name cannot be empty");
        }

        if (!APP_NAME_PATTERN.matcher(appName).matches()) {
            throw new SecurityException(
                "Invalid application name. Must start with a letter and contain only letters, digits, hyphens, or underscores (max 32 chars)."
            );
        }
    }

    /**
     * Validates a Java package name.
     */
    public void validatePackageName(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            throw new SecurityException("Package name cannot be empty");
        }

        if (!PACKAGE_NAME_PATTERN.matcher(packageName).matches()) {
            throw new SecurityException(
                "Invalid package name. Must follow Java package naming conventions (lowercase, dots allowed)."
            );
        }

        if (packageName.length() > 200) {
            throw new SecurityException("Package name too long (max 200 chars)");
        }
    }

}
