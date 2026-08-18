package com.api.generator.api.service;

import com.api.generator.account.repo.GeneratedApiRepository;
import com.api.generator.config.GenerationJobProperties;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationServiceWorkerModeTest {

    @Test
    void workerDisabledDoesNotSkipLiveWatchLoop() {
        GeneratedApiRepository repo = mock(GeneratedApiRepository.class);
        GenerationJobService jobService = mock(GenerationJobService.class);
        GenerationJobProperties properties = new GenerationJobProperties();
        properties.setWorkerEnabled(false);
        UUID generatedApiId = UUID.randomUUID();
        when(repo.findById(generatedApiId)).thenReturn(Optional.empty());

        GenerationService service = new GenerationService(repo, jobService, properties);

        assertThrows(NoSuchElementException.class, () -> service.watchJob(generatedApiId, "job-1"));
        verify(repo).findById(generatedApiId);
    }
}
