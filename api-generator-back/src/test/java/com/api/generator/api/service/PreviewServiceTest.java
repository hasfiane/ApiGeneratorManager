package com.api.generator.api.service;

import com.api.generator.account.ApiPreview;
import com.api.generator.account.GeneratedApi;
import com.api.generator.account.GenerationStatus;
import com.api.generator.account.PreviewStatus;
import com.api.generator.account.repo.ApiPreviewRepository;
import com.api.generator.config.GenerationJobProperties;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreviewServiceTest {

    @Mock
    private ApiPreviewRepository repo;

    @Mock
    private PreviewRuntimeService runtimeService;

    @Mock
    private PreviewConfigCodec previewConfigCodec;

    private GeneratedApi generatedApi;
    private AtomicReference<ApiPreview> savedPreview;
    private PreviewService service;

    @BeforeEach
    void setUp() throws Exception {
        GenerationJobProperties jobProperties = new GenerationJobProperties();
        jobProperties.setPreviewHealthTimeoutSeconds(5);
        jobProperties.setPreviewStartupTimeoutSeconds(30);
        jobProperties.setPreviewRetentionHours(12);
        jobProperties.setPreviewHealthProbePaths(List.of("/actuator/health"));

        generatedApi = new GeneratedApi();
        setGeneratedApiId(generatedApi, UUID.randomUUID());
        generatedApi.setName("PreviewServiceTest");
        generatedApi.setStatus(GenerationStatus.DONE);
        generatedApi.setPreviewConfigJson("{encoded}");
        generatedApi.setFilePath(Files.createTempFile("preview-service-test-", ".zip").toString());

        ApiPreview preview = new ApiPreview();
        preview.setGeneratedApi(generatedApi);
        preview.setStatus(PreviewStatus.STARTING);
        preview.setCreatedAt(Instant.now());

        savedPreview = new AtomicReference<>(preview);

        lenient().when(repo.findByGeneratedApi_Id(generatedApi.getId())).thenAnswer(invocation -> Optional.of(savedPreview.get()));
        lenient().when(repo.save(any(ApiPreview.class))).thenAnswer(invocation -> {
            ApiPreview candidate = invocation.getArgument(0);
            savedPreview.set(candidate);
            return candidate;
        });

        service = new PreviewService(repo, runtimeService, previewConfigCodec, jobProperties);
    }

    @Test
    void startAsyncPersistsStructuredFailureForBuildErrors() throws Exception {
        Logger previewLogger = (Logger) LoggerFactory.getLogger(PreviewService.class);
        Level previousLevel = previewLogger.getLevel();
        previewLogger.setLevel(Level.OFF);
        try {
            when(previewConfigCodec.decode("{encoded}")).thenReturn(new PreviewRuntimeService.PreviewLaunchConfig(
                    "postgres",
                    "jdbc:postgresql://localhost/test",
                    "user",
                    "pass",
                    "public",
                    "admin",
                    "bootstrap-password",
                    "jwt-secret-value-012345678901234567890123",
                    "issuer",
                    3600
            ));
            doThrow(new IllegalStateException("Command failed (1): mvn clean verify"))
                    .when(runtimeService)
                    .start(any(GeneratedApi.class), any(PreviewRuntimeService.PreviewLaunchConfig.class));

            service.startAsync(generatedApi.getId());

            ApiPreview failedPreview = savedPreview.get();
            assertThat(failedPreview.getStatus()).isEqualTo(PreviewStatus.FAILED);
            assertThat(failedPreview.getErrorCode()).isEqualTo("PREVIEW_BUILD_FAILED");
            assertThat(failedPreview.getErrorHint()).contains("generated project build failed");
            assertThat(failedPreview.getErrorMessage()).contains("Command failed (1)");
        } finally {
            previewLogger.setLevel(previousLevel);
        }
    }

    @Test
    void diagnosticsRecommendFixingHostChecksWhenRuntimeIsNotReady() {
        when(runtimeService.diagnoseHost()).thenReturn(new PreviewRuntimeService.HostDiagnostics(
                "docker",
                List.of(
                        new PreviewRuntimeService.HostCheck("containerRuntimeBinary", true, "binary ok"),
                        new PreviewRuntimeService.HostCheck("containerRuntimeReachable", false, "runtime unreachable"),
                        new PreviewRuntimeService.HostCheck("mavenCommandAvailable", true, "maven ok")
                )
        ));

        PreviewService.PreviewDiagnostics diagnostics = service.diagnostics(generatedApi);

        assertThat(diagnostics.hostReady()).isFalse();
        assertThat(diagnostics.recommendedAction()).isNotNull();
        assertThat(diagnostics.recommendedAction().code()).isEqualTo("FIX_HOST_DIAGNOSTICS");
        assertThat(diagnostics.hostChecks()).hasSize(3);
    }

    private void setGeneratedApiId(GeneratedApi api, UUID id) throws Exception {
        Field field = GeneratedApi.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(api, id);
    }
}
