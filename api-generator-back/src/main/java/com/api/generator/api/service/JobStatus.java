package com.api.generator.api.service;

/**
 * Possible states for an API generation job.
 */
public enum JobStatus {
    /** Job created but not started yet. */
    PENDING,
    /** Job currently generating (DB introspection + code writing). */
    RUNNING,
    /** Job currently building the generated project (mvnw package). */
    BUILDING,
    /** Job currently building a Docker image for the generated project. */
    DOCKER_BUILDING,
    /** Job deployed locally (docker container running). */
    DEPLOYED,
    /** Job finished successfully and a zip is available. */
    SUCCEEDED,
    /** Job was stopped (container removed). */
    STOPPED,
    /** Job failed (see error message). */
    FAILED
}
