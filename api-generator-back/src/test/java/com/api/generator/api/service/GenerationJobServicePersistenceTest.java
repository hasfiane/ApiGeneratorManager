package com.api.generator.api.service;

import com.api.generator.api.persistence.GenerationJobRecord;
import com.api.generator.api.persistence.GenerationJobRecordRepository;
import com.api.generator.auth.JwtProperties;
import com.api.generator.config.GenerationJobProperties;
import com.api.generator.config.SensitivePayloadProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationJobServicePersistenceTest {

    private static GenerationPayloadCodec payloadCodec() {
        SensitivePayloadProperties properties = new SensitivePayloadProperties();
        properties.setCurrentKey("test-current-key-32-characters-minimum");
        return new GenerationPayloadCodec(
                properties,
                new JwtProperties("test-jwt-secret-at-least-32-characters", "api-generator-manager", 3600)
        );
    }

    @Test
    void readsPersistedJobAndLogsWhenInMemoryStateIsMissing() {
        GenerationJobRecordRepository repository = mock(GenerationJobRecordRepository.class);
        GenerationJobRecord record = new GenerationJobRecord();
        record.setJobId("job-123");
        record.setStatus(JobStatus.FAILED);
        record.setCreatedAt(Instant.parse("2026-04-24T10:15:30Z"));
        record.setUpdatedAt(Instant.parse("2026-04-24T10:16:30Z"));
        record.setErrorMessage("Generation interrupted by manager restart.");
        record.setZipPath("C:/tmp/generated-api.zip");
        record.setOutputDir("C:/tmp/generated-api");
        record.setLogs("line-1\nline-2\nline-3");
        record.setUserLogs("step-1\nstep-2");

        when(repository.findById("job-123")).thenReturn(Optional.of(record));

        GenerationJobService service = new GenerationJobService(new GenerationJobProperties(), null, repository, payloadCodec());

        JobInfo info = service.getJob("job-123").orElseThrow();

        assertEquals(JobStatus.FAILED, info.status());
        assertEquals("Generation interrupted by manager restart.", info.error());
        assertEquals(Path.of("C:/tmp/generated-api.zip").normalize(), info.zipPath().normalize());
        assertEquals(2, service.getLogs("job-123", 2, false).size());
        assertEquals("line-2", service.getLogs("job-123", 2, false).get(0));
        assertEquals("step-1", service.getLogs("job-123", 10, true).get(0));
        assertTrue(service.getZipIfReady("job-123").isEmpty());
    }

    @Test
    void recoveryOnlyMarksRunningStatesAsInterrupted() {
        GenerationJobRecordRepository repository = mock(GenerationJobRecordRepository.class);
        GenerationJobRecord running = new GenerationJobRecord();
        running.setJobId("job-running");
        running.setStatus(JobStatus.RUNNING);
        running.setCreatedAt(Instant.now());
        running.setUpdatedAt(Instant.now());

        when(repository.findAllByStatusIn(List.of(
                JobStatus.RUNNING,
                JobStatus.BUILDING,
                JobStatus.DOCKER_BUILDING
        ))).thenReturn(List.of(running));

        GenerationJobService service = new GenerationJobService(new GenerationJobProperties(), null, repository, payloadCodec());
        service.recoverInterruptedPersistedJobs();

        assertEquals(JobStatus.FAILED, running.getStatus());
        assertEquals("Generation interrupted by manager restart.", running.getErrorMessage());
        verify(repository).save(running);
        verify(repository, never()).findAllByStatusIn(List.of(JobStatus.PENDING));
    }
}
