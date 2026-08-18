package com.api.generator.api.service;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Immutable snapshot of a generation job.
 * <p>
 * In a future cloud deployment, this can be persisted.
 */
public record JobInfo(
        String jobId,
        JobStatus status,
        Instant createdAt,
        String error,
        Path zipPath,
        Path outputDir,
        Integer hostPort,
        String apiBaseUrl,
        String containerId
) {
}
