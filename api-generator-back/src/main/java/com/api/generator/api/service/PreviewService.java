package com.api.generator.api.service;

import com.api.generator.account.ApiPreview;
import com.api.generator.account.GeneratedApi;
import com.api.generator.account.GenerationStatus;
import com.api.generator.account.PreviewStatus;
import com.api.generator.account.repo.ApiPreviewRepository;
import jakarta.transaction.Transactional;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@Slf4j
public class PreviewService {
    private static final int RUNTIME_LOG_TAIL = 300;
    private static final long PREVIEW_POLL_INTERVAL_NANOS = Duration.ofSeconds(2).toNanos();

    private final ApiPreviewRepository repo;
    private final PreviewRuntimeService runtimeService;
    private final PreviewConfigCodec previewConfigCodec;
    private final com.api.generator.config.GenerationJobProperties jobProperties;
    private final HttpClient httpClient;

    public PreviewService(ApiPreviewRepository repo,
                          PreviewRuntimeService runtimeService,
                          PreviewConfigCodec previewConfigCodec,
                          com.api.generator.config.GenerationJobProperties jobProperties) {
        this.repo = repo;
        this.runtimeService = runtimeService;
        this.previewConfigCodec = previewConfigCodec;
        this.jobProperties = jobProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public ApiPreview getOrCreate(GeneratedApi generatedApi) {
        return repo.findByGeneratedApi_Id(generatedApi.getId())
                .orElseGet(() -> {
                    ApiPreview preview = new ApiPreview();
                    preview.setGeneratedApi(generatedApi);
                    preview.setStatus(PreviewStatus.STOPPED);
                    preview.setCreatedAt(Instant.now());
                    return repo.save(preview);
                });
    }

    public ApiPreview getByGeneratedApi(GeneratedApi generatedApi) {
        return repo.findByGeneratedApi_Id(generatedApi.getId())
                .orElseGet(() -> {
                    ApiPreview preview = new ApiPreview();
                    preview.setGeneratedApi(generatedApi);
                    preview.setStatus(PreviewStatus.STOPPED);
                    preview.setCreatedAt(Instant.now());
                    return preview;
                });
    }

    public PreviewDiagnostics diagnostics(GeneratedApi generatedApi) {
        ApiPreview preview = getByGeneratedApi(generatedApi);
        PreviewRuntimeService.HostDiagnostics hostDiagnostics = runtimeService.diagnoseHost();
        boolean generationDone = generatedApi.getStatus() == GenerationStatus.DONE;
        boolean previewConfigAvailable = generatedApi.getPreviewConfigJson() != null && !generatedApi.getPreviewConfigJson().isBlank();
        boolean zipAvailable = generatedApi.getFilePath() != null
                && !generatedApi.getFilePath().isBlank()
                && java.nio.file.Files.exists(java.nio.file.Path.of(generatedApi.getFilePath()));
        boolean hostReady = hostDiagnostics.checks().stream().allMatch(PreviewRuntimeService.HostCheck::ok);
        return new PreviewDiagnostics(
                generatedApi.getStatus() == null ? null : generatedApi.getStatus().name(),
                preview.getStatus() == null ? null : preview.getStatus().name(),
                generationDone,
                previewConfigAvailable,
                zipAvailable,
                hostReady,
                hostDiagnostics.containerRuntime(),
                hostDiagnostics.checks(),
                recommendNextAction(generationDone, previewConfigAvailable, zipAvailable, hostReady, preview)
        );
    }

    public java.util.List<String> logs(GeneratedApi generatedApi, int tail) {
        ApiPreview preview = getByGeneratedApi(generatedApi);
        if (preview.getId() == null) {
            return java.util.List.of();
        }
        try {
            List<String> runtimeLogs = runtimeService.logs(preview, tail);
            if (!runtimeLogs.isEmpty()) {
                updatePersistedLogs(preview, runtimeLogs);
                return runtimeLogs;
            }
        } catch (Exception e) {
            appendPersistedLog(preview, "ERROR: " + (e.getMessage() == null ? "Unable to fetch preview logs" : e.getMessage()));
        }
        return tailPersistedLogs(preview.getLogs(), tail);
    }

    @Transactional
    public ApiPreview requestStart(GeneratedApi generatedApi) {
        if (generatedApi.getStatus() != GenerationStatus.DONE) {
            throw new ResponseStatusException(BAD_REQUEST, "Preview is only available after generation is done");
        }
        if (generatedApi.getDbType() != null && generatedApi.getDbType().equalsIgnoreCase("h2")) {
            throw new ResponseStatusException(BAD_REQUEST, "H2 demo does not support Docker preview. Use PostgreSQL or MySQL for an isolated live runtime.");
        }
        if (generatedApi.getPreviewConfigJson() == null || generatedApi.getPreviewConfigJson().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Preview configuration is not available");
        }
        ApiPreview preview = getOrCreate(generatedApi);
        if (preview.getStatus() == PreviewStatus.STARTING || preview.getStatus() == PreviewStatus.RUNNING) {
            return preview;
        }
        preview.setStatus(PreviewStatus.STARTING);
        preview.setErrorMessage(null);
        preview.setStartedAt(null);
        preview.setStoppedAt(null);
        preview.setBaseUrl(null);
        preview.setHostPort(null);
        preview.setContainerId(null);
        preview.setImageTag(null);
        preview.setWorkspaceDir(null);
        preview.setErrorCode(null);
        preview.setErrorHint(null);
        preview.setLogs("");
        appendPersistedLog(preview, "Preview start requested");
        return repo.save(preview);
    }

    @Async
    public void startAsync(UUID generatedApiId) {
        ApiPreview preview = repo.findByGeneratedApi_Id(generatedApiId).orElseThrow();
        PreviewRuntimeService.StartResult result = null;
        try {
            GeneratedApi generatedApi = preview.getGeneratedApi();
            if (generatedApi.getPreviewConfigJson() == null || generatedApi.getPreviewConfigJson().isBlank()) {
                throw new IllegalStateException("Preview configuration is not available");
            }
            PreviewRuntimeService.PreviewLaunchConfig config = previewConfigCodec.decode(generatedApi.getPreviewConfigJson());
            appendPersistedLog(preview, "Building preview runtime");
            result = runtimeService.start(generatedApi, config);
            applyRuntimeMetadata(preview, result);
            preview.setStartedAt(Instant.now());
            preview.setStoppedAt(null);
            preview.setErrorMessage(null);
            preview.setErrorCode(null);
            preview.setErrorHint(null);
            appendPersistedLog(preview, "Preview container started on " + result.baseUrl());
            repo.save(preview);

            waitUntilResponsive(result.baseUrl());

            preview.setStatus(PreviewStatus.RUNNING);
            appendPersistedLog(preview, "Preview is reachable");
            repo.save(preview);
        } catch (Exception e) {
            log.error("Unable to start preview for generated API {}", generatedApiId, e);
            if (result != null) {
                captureRuntimeLogs(preview);
                cleanupRuntime(preview);
            }
            FailureDetails failure = classifyFailure(e);
            preview.setStatus(PreviewStatus.FAILED);
            preview.setErrorMessage(failure.message());
            preview.setErrorCode(failure.code());
            preview.setErrorHint(failure.hint());
            preview.setStoppedAt(Instant.now());
            appendPersistedLog(preview, "ERROR[" + failure.code() + "]: " + failure.message());
            repo.save(preview);
        }
    }

    @Transactional
    public ApiPreview stop(GeneratedApi generatedApi) {
        ApiPreview preview = getOrCreate(generatedApi);
        if (preview.getStatus() == PreviewStatus.STOPPED) {
            return preview;
        }
        preview.setStatus(PreviewStatus.STOPPING);
        appendPersistedLog(preview, "Preview stop requested");
        repo.save(preview);
        try {
            captureRuntimeLogs(preview);
            runtimeService.stop(preview);
            preview.setStatus(PreviewStatus.STOPPED);
            preview.setStoppedAt(Instant.now());
            preview.setBaseUrl(null);
            preview.setHostPort(null);
            preview.setContainerId(null);
            preview.setImageTag(null);
            preview.setWorkspaceDir(null);
            preview.setErrorMessage(null);
            preview.setErrorCode(null);
            preview.setErrorHint(null);
            appendPersistedLog(preview, "Preview stopped");
            return repo.save(preview);
        } catch (Exception e) {
            FailureDetails failure = classifyFailure(e);
            preview.setStatus(PreviewStatus.FAILED);
            preview.setErrorMessage(failure.message());
            preview.setErrorCode(failure.code());
            preview.setErrorHint(failure.hint());
            preview.setStoppedAt(Instant.now());
            appendPersistedLog(preview, "ERROR[" + failure.code() + "]: " + failure.message());
            return repo.save(preview);
        }
    }

    @Transactional
    public ApiPreview restart(GeneratedApi generatedApi) {
        stop(generatedApi);
        return requestStart(generatedApi);
    }

    @PreDestroy
    public void shutdownRunningPreviews() {
        for (ApiPreview preview : repo.findByStatusIn(List.of(PreviewStatus.STARTING, PreviewStatus.RUNNING, PreviewStatus.STOPPING))) {
            try {
                captureRuntimeLogs(preview);
                cleanupRuntime(preview);
                preview.setStatus(PreviewStatus.STOPPED);
                preview.setStoppedAt(Instant.now());
                preview.setErrorMessage(null);
                appendPersistedLog(preview, "Preview stopped during manager shutdown");
                repo.save(preview);
            } catch (Exception e) {
                log.warn("Unable to stop preview {} during shutdown", preview.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 900000L)
    public void cleanupRetainedPreviews() {
        Instant now = Instant.now();
        Instant terminalCutoff = now.minus(Duration.ofHours(jobProperties.getPreviewRetentionHours()));
        Instant startupCutoff = now.minus(Duration.ofSeconds(jobProperties.getPreviewStartupTimeoutSeconds()));

        for (ApiPreview preview : repo.findByStatusIn(List.of(
                PreviewStatus.STOPPED,
                PreviewStatus.FAILED,
                PreviewStatus.STARTING,
                PreviewStatus.STOPPING
        ))) {
            try {
                if (isStaleTerminal(preview, terminalCutoff)) {
                    captureRuntimeLogs(preview);
                    cleanupRuntime(preview);
                    preview.setErrorMessage(null);
                    repo.save(preview);
                    continue;
                }
                if (isStuckTransition(preview, startupCutoff)) {
                    captureRuntimeLogs(preview);
                    cleanupRuntime(preview);
                    preview.setStatus(PreviewStatus.FAILED);
                    FailureDetails failure = classifyFailure(new IllegalStateException("Preview startup timed out"));
                    preview.setErrorMessage(failure.message());
                    preview.setErrorCode(failure.code());
                    preview.setErrorHint(failure.hint());
                    preview.setStoppedAt(now);
                    appendPersistedLog(preview, "ERROR[" + failure.code() + "]: " + failure.message());
                    repo.save(preview);
                }
            } catch (Exception e) {
                log.warn("Unable to cleanup preview {}", preview.getId(), e);
            }
        }
    }

    private boolean isStaleTerminal(ApiPreview preview, Instant cutoff) {
        if (preview.getStatus() != PreviewStatus.STOPPED && preview.getStatus() != PreviewStatus.FAILED) {
            return false;
        }
        Instant terminalAt = preview.getStoppedAt() == null ? preview.getCreatedAt() : preview.getStoppedAt();
        return terminalAt != null && terminalAt.isBefore(cutoff);
    }

    private boolean isStuckTransition(ApiPreview preview, Instant cutoff) {
        if (preview.getStatus() != PreviewStatus.STARTING && preview.getStatus() != PreviewStatus.STOPPING) {
            return false;
        }
        Instant startedAt = preview.getStartedAt() == null ? preview.getCreatedAt() : preview.getStartedAt();
        return startedAt != null && startedAt.isBefore(cutoff);
    }

    private void waitUntilResponsive(String baseUrl) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(jobProperties.getPreviewHealthTimeoutSeconds()));
        Exception lastError = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                if (isResponsive(baseUrl)) {
                    return;
                }
            } catch (Exception e) {
                lastError = e;
            }
            LockSupport.parkNanos(PREVIEW_POLL_INTERVAL_NANOS);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Preview responsiveness wait interrupted");
            }
        }
        if (lastError != null) {
            throw new IllegalStateException("Preview did not become reachable in time", lastError);
        }
        throw new IllegalStateException("Preview did not become reachable in time");
    }

    private boolean isResponsive(String baseUrl) throws Exception {
        for (String path : jobProperties.getPreviewHealthProbePaths()) {
            if (path == null || path.isBlank()) {
                continue;
            }
            ProbeResult probe = probe(baseUrl, normalizeProbePath(path));
            if (!probe.available()) {
                continue;
            }
            if (probe.ready()) {
                return true;
            }
            if (probe.strict()) {
                throw new IllegalStateException("Preview probe " + probe.path() + " is not ready");
            }
        }
        return false;
    }

    private ProbeResult probe(String baseUrl, String path) throws Exception {
        if (isStrictHealthPath(path)) {
            return probeHealth(baseUrl, path);
        }
        return probeHttp(baseUrl, path);
    }

    private ProbeResult probeHealth(String baseUrl, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return new ProbeResult(path, false, false, true);
            }
            if (response.statusCode() >= 500) {
                return new ProbeResult(path, true, false, true);
            }
            String body = response.body() == null ? "" : response.body();
            boolean isUp = body.contains("\"status\"") && body.toUpperCase().contains("UP");
            return new ProbeResult(path, true, isUp, true);
        } catch (IOException e) {
            if (e instanceof ConnectException || e.getCause() instanceof ConnectException) {
                return new ProbeResult(path, false, false, true);
            }
            throw e;
        }
    }

    private ProbeResult probeHttp(String baseUrl, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 404) {
                return new ProbeResult(path, false, false, false);
            }
            return new ProbeResult(path, true, response.statusCode() < 500, false);
        } catch (IOException e) {
            if (e instanceof ConnectException || e.getCause() instanceof ConnectException) {
                return new ProbeResult(path, false, false, false);
            }
            throw e;
        }
    }

    private boolean isStrictHealthPath(String path) {
        return path.toLowerCase(Locale.ROOT).contains("health");
    }

    private String normalizeProbePath(String path) {
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            return "/";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private void applyRuntimeMetadata(ApiPreview preview, PreviewRuntimeService.StartResult result) {
        preview.setContainerId(result.containerId());
        preview.setImageTag(result.imageTag());
        preview.setWorkspaceDir(result.workspaceDir());
        preview.setHostPort(result.hostPort());
        preview.setBaseUrl(result.baseUrl());
    }

    private void cleanupRuntime(ApiPreview preview) {
        try {
            runtimeService.stop(preview);
        } catch (Exception e) {
            log.debug("Preview cleanup failed for {}", preview.getId(), e);
        }
        preview.setBaseUrl(null);
        preview.setHostPort(null);
        preview.setContainerId(null);
        preview.setImageTag(null);
        preview.setWorkspaceDir(null);
    }

    private void captureRuntimeLogs(ApiPreview preview) {
        if (preview.getId() == null) {
            return;
        }
        try {
            List<String> runtimeLogs = runtimeService.logs(preview, RUNTIME_LOG_TAIL);
            if (!runtimeLogs.isEmpty()) {
                updatePersistedLogs(preview, runtimeLogs);
            }
        } catch (Exception e) {
            log.debug("Unable to capture runtime logs for {}", preview.getId(), e);
        }
    }

    private void updatePersistedLogs(ApiPreview preview, List<String> lines) {
        preview.setLogs(mergeLogHistory(preview.getLogs(), lines));
        repo.save(preview);
    }

    private void appendPersistedLog(ApiPreview preview, String message) {
        String existing = preview.getLogs() == null ? "" : preview.getLogs();
        String entry = Instant.now() + " - " + (message == null ? "" : message.trim());
        preview.setLogs(existing.isBlank() ? entry : existing + "\n" + entry);
    }

    private List<String> tailPersistedLogs(String logs, int tail) {
        if (logs == null || logs.isBlank()) {
            return List.of();
        }
        String[] lines = Arrays.stream(logs.split("\\R"))
                .filter(line -> !line.isBlank())
                .toArray(String[]::new);
        int fromIndex = Math.max(0, lines.length - Math.max(1, tail));
        return Arrays.asList(Arrays.copyOfRange(lines, fromIndex, lines.length));
    }

    private String mergeLogHistory(String existingLogs, List<String> freshLines) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existingLogs != null && !existingLogs.isBlank()) {
            Arrays.stream(existingLogs.split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .forEach(merged::add);
        }
        freshLines.stream()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .forEach(merged::add);

        List<String> ordered = merged.stream().toList();
        int max = Math.max(1, jobProperties.getMaxLogLines());
        int fromIndex = Math.max(0, ordered.size() - max);
        return String.join("\n", ordered.subList(fromIndex, ordered.size()));
    }

    private FailureDetails classifyFailure(Exception error) {
        String message = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "Preview failed"
                : error.getMessage().trim();

        if (message.startsWith("Container runtime binary is not available:")) {
            return new FailureDetails(
                    "HOST_RUNTIME_BINARY_MISSING",
                    message,
                    "Install the configured container runtime on the manager host, then restart preview."
            );
        }
        if (message.startsWith("Container runtime is not reachable:")) {
            return new FailureDetails(
                    "HOST_RUNTIME_UNREACHABLE",
                    message,
                    "Start Docker or Podman on the manager host before launching preview."
            );
        }
        switch (message) {
            case "Maven build command is not available for preview runtime" -> {
                return new FailureDetails(
                        "HOST_MAVEN_UNAVAILABLE",
                        message,
                        "Expose a working mvn or mvnw command on the manager host so the generated project can be built."
                );
            }
            case "Generated ZIP is not available" -> {
                return new FailureDetails(
                        "PREVIEW_ZIP_MISSING",
                        message,
                        "Run a successful generation first so preview can rebuild from the persisted ZIP."
                );
            }
            case "Preview configuration is not available" -> {
                return new FailureDetails(
                        "PREVIEW_CONFIG_MISSING",
                        message,
                        "Regenerate the API so a fresh preview launch configuration is persisted."
                );
            }
            case "Preview startup timed out", "Preview did not become reachable in time" -> {
                return new FailureDetails(
                        "PREVIEW_STARTUP_TIMEOUT",
                        message,
                        "Inspect preview logs and health endpoints to understand why the generated app never became reachable."
                );
            }
            default -> {
                // Fall through to the remaining command-specific classification below.
            }
        }
        if (message.startsWith("Preview probe ") && message.endsWith(" is not ready")) {
            return new FailureDetails(
                    "PREVIEW_HEALTH_NOT_READY",
                    message,
                    "The generated app answered the probe but did not report readiness yet. Check /actuator/health and preview logs."
            );
        }
        if (message.startsWith("Command timed out:")) {
            return new FailureDetails(
                    "PREVIEW_COMMAND_TIMEOUT",
                    message,
                    "A preview build or runtime command exceeded the configured timeout. Inspect host load and preview logs."
            );
        }
        if (message.startsWith("Command failed (")) {
            switch (classifyFailedCommand(message)) {
                case BUILD -> {
                    return new FailureDetails(
                            "PREVIEW_BUILD_FAILED",
                            message,
                            "The generated project build failed during preview startup. Inspect preview logs and local Maven artifacts."
                    );
                }
                case IMAGE_BUILD -> {
                    return new FailureDetails(
                            "PREVIEW_IMAGE_BUILD_FAILED",
                            message,
                            "The preview image build failed. Verify the container runtime and generated Docker files."
                    );
                }
                case CONTAINER_START -> {
                    return new FailureDetails(
                            "PREVIEW_CONTAINER_START_FAILED",
                            message,
                            "The preview container could not start. Inspect the runtime state, host port allocation, and logs."
                    );
                }
                case UNKNOWN -> {
                    // Fall through to the generic failure below.
                }
            }
        }
        return new FailureDetails(
                "PREVIEW_UNKNOWN_FAILURE",
                message,
                "Inspect the preview logs and host diagnostics for more details."
        );
    }
    private FailedCommandType classifyFailedCommand(String message) {
        if (message.contains("clean package") || message.contains("mvn")) {
            return FailedCommandType.BUILD;
        }
        if (message.contains(" build ") || message.contains("docker build") || message.contains("podman build")) {
            return FailedCommandType.IMAGE_BUILD;
        }
        if (message.contains(" run ") || message.contains("docker run") || message.contains("podman run")) {
            return FailedCommandType.CONTAINER_START;
        }
        return FailedCommandType.UNKNOWN;
    }

    private Recommendation recommendNextAction(boolean generationDone,
                                               boolean previewConfigAvailable,
                                               boolean zipAvailable,
                                               boolean hostReady,
                                               ApiPreview preview) {
        if (!generationDone) {
            return new Recommendation("WAIT_FOR_GENERATION", "Wait until generation reaches DONE before starting preview.");
        }
        if (!previewConfigAvailable) {
            return new Recommendation("REGENERATE_FOR_PREVIEW_CONFIG", "Regenerate the API so preview configuration is persisted again.");
        }
        if (!zipAvailable) {
            return new Recommendation("REGENERATE_FOR_ZIP", "Run a successful generation again to restore the generated ZIP artifact.");
        }
        if (!hostReady) {
            return new Recommendation("FIX_HOST_DIAGNOSTICS", "Fix the failing host checks before launching preview.");
        }
        if (preview.getStatus() == PreviewStatus.FAILED && preview.getErrorHint() != null && !preview.getErrorHint().isBlank()) {
            return new Recommendation("FOLLOW_FAILURE_HINT", preview.getErrorHint());
        }
        return new Recommendation("START_PREVIEW", "Host checks are green. You can start or restart preview safely.");
    }

    public record PreviewDiagnostics(
            String generationStatus,
            String previewStatus,
            boolean generationDone,
            boolean previewConfigAvailable,
            boolean zipAvailable,
            boolean hostReady,
            String containerRuntime,
            List<PreviewRuntimeService.HostCheck> hostChecks,
            Recommendation recommendedAction
    ) {
    }

    private record FailureDetails(String code, String message, String hint) {
    }

    private enum FailedCommandType {
        BUILD,
        IMAGE_BUILD,
        CONTAINER_START,
        UNKNOWN
    }

    public record Recommendation(String code, String message) {
    }

    private record ProbeResult(String path, boolean available, boolean ready, boolean strict) {
    }
}
