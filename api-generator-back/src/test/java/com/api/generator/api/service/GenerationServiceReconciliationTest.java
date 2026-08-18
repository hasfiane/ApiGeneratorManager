package com.api.generator.api.service;

import com.api.generator.account.GeneratedApi;
import com.api.generator.account.GenerationStatus;
import com.api.generator.account.repo.GeneratedApiRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationServiceReconciliationTest {

    @Test
    void reconcilePendingGeneratedApiMarksDoneFromPersistedJobState() {
        GeneratedApiRepository repo = mock(GeneratedApiRepository.class);
        GenerationJobService jobService = mock(GenerationJobService.class);
        GenerationService service = new GenerationService(repo, jobService);

        UUID id = UUID.randomUUID();
        GeneratedApi api = new GeneratedApi();
        ReflectionTestUtils.setField(api, "id", id);
        api.setName("demo");
        api.setStatus(GenerationStatus.PENDING);
        api.setProgress(5);
        api.setLogs("");
        api.setCreatedAt(Instant.now());

        JobInfo job = new JobInfo(
                "job-1",
                JobStatus.SUCCEEDED,
                Instant.now(),
                null,
                Path.of("C:/tmp/generated-api.zip"),
                Path.of("C:/tmp/generated-api"),
                null,
                null,
                null
        );

        when(repo.findById(id)).thenReturn(Optional.of(api));
        when(repo.save(any(GeneratedApi.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobService.getJob("job-1")).thenReturn(Optional.of(job));
        when(jobService.getLogs("job-1", Integer.MAX_VALUE, true)).thenReturn(List.of(
                "generation.started",
                "generation.templateReady",
                "generation.zipReady",
                "generation.buildReady",
                "generation.done"
        ));

        service.reconcilePendingGeneratedApi(id, "job-1");

        assertEquals(GenerationStatus.DONE, api.getStatus());
        assertEquals(100, api.getProgress());
        assertEquals(Path.of("C:/tmp/generated-api.zip").normalize().toString(), Path.of(api.getFilePath()).normalize().toString());
        assertEquals(true, api.getLogs().contains("Start generation"));
        assertEquals(true, api.getLogs().contains("Build complete"));
        assertEquals(false, api.getLogs().contains("Generation synchronized from persisted job state."));
    }
}
