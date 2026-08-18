package com.api.generator.api.service;

import com.api.generator.api.persistence.GenerationJobRecordRepository;
import com.api.generator.auth.JwtProperties;
import com.api.generator.config.GenerationJobProperties;
import com.api.generator.config.SensitivePayloadProperties;
import com.api.generator.config.GeneratorProperties;
import com.api.generator.reader.SchemaReader;
import com.api.generator.schema.DatabaseType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenerationJobServiceWorkerModeTest {

    private static GenerationPayloadCodec payloadCodec() {
        SensitivePayloadProperties properties = new SensitivePayloadProperties();
        properties.setCurrentKey("test-current-key-32-characters-minimum");
        return new GenerationPayloadCodec(
                properties,
                new JwtProperties("test-jwt-secret-at-least-32-characters", "api-generator-manager", 3600)
        );
    }

    @Test
    void workerDisabledSkipsRecoveryCleanupAndPolling() {
        GenerationJobProperties properties = new GenerationJobProperties();
        properties.setWorkerEnabled(false);
        GenerationJobRecordRepository repository = mock(GenerationJobRecordRepository.class);
        GenerationJobService service = new GenerationJobService(properties, null, repository, payloadCodec());

        service.recoverInterruptedPersistedJobs();
        service.cleanupFinishedJobs();
        service.pollPersistedPendingJobs();

        verifyNoInteractions(repository);
    }

    @Test
    void requestOnlyBackendQueuesPersistedJobInsteadOfExecutingLocally() throws Exception {
        GenerationJobProperties properties = new GenerationJobProperties();
        properties.setWorkerEnabled(false);
        properties.setDockerRequestEnabled(true);
        properties.setDockerDeploymentEnabled(false);
        GenerationJobRecordRepository repository = mock(GenerationJobRecordRepository.class);
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        SchemaReader schemaReader = mock(SchemaReader.class);
        GenerationJobService service = new GenerationJobService(properties, schemaReader, repository, payloadCodec());

        service.startGeneration(generatorProperties(), true, true, true, 18080);

        verifyNoInteractions(schemaReader);
    }

    private static GeneratorProperties generatorProperties() {
        GeneratorProperties props = new GeneratorProperties();
        props.setAppName("QueuedApi");
        props.setBasePackage("com.example.queued");
        props.getDb().setType(DatabaseType.POSTGRESQL);
        props.getDb().setUrl("jdbc:postgresql://db.example.com:5432/app");
        props.getDb().setUsername("user");
        props.getDb().setPassword("password");
        props.getDb().setSchema("public");
        return props;
    }
}
