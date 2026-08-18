package com.api.generator.api.service;

import com.api.generator.account.AppUser;
import com.api.generator.account.GeneratedApi;
import com.api.generator.account.GenerationStatus;
import com.api.generator.account.repo.GeneratedApiRepository;
import com.api.generator.config.GenerationJobProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Slf4j
public class GenerationService {

    private static final long WATCH_POLL_MS = 750L;
    private static final String MISSING_JOB_MESSAGE = "Generation job is no longer available on this server.";

    private final GeneratedApiRepository repo;
    private final GenerationJobService jobService;

    public GenerationService(GeneratedApiRepository repo, GenerationJobService jobService) {
        this.repo = repo;
        this.jobService = jobService;
    }

    @Autowired
    public GenerationService(GeneratedApiRepository repo,
                             GenerationJobService jobService,
                             GenerationJobProperties ignoredJobProperties) {
        this(repo, jobService);
    }

    public GeneratedApi createPending(String name, String dbType, AppUser user) {
        GeneratedApi api = new GeneratedApi();
        api.setName(name == null || name.isBlank() ? "generated-api" : name);
        api.setStatus(GenerationStatus.PENDING);
        api.setDbType(dbType);
        api.setProgress(0);
        api.setLogs("");
        api.setCreatedAt(Instant.now());
        api.setUser(user);
        return repo.save(api);
    }

    public void attachJob(GeneratedApi api, String jobId) {
        api.setJobId(jobId);
        GeneratedApi saved = persist(api);
        appendLog(saved, "Generation queued");
        updateProgress(saved, 5);
    }

    public void updatePreviewConfig(GeneratedApi api, String previewConfigJson) {
        api.setPreviewConfigJson(previewConfigJson);
        persist(api);
    }

    public void markDone(UUID id, String path, String apiBaseUrl) {
        GeneratedApi api = repo.findById(id).orElseThrow();
        api.setStatus(GenerationStatus.DONE);
        api.setFilePath(path);
        api.setApiBaseUrl(apiBaseUrl);
        api.setProgress(100);
        api.setFinishedAt(Instant.now());
        api.setErrorMessage(null);
        persist(api);
    }

    public void markFailed(UUID id, String error) {
        GeneratedApi api = repo.findById(id).orElseThrow();
        api.setStatus(GenerationStatus.FAILED);
        api.setErrorMessage(error);
        api.setApiBaseUrl(null);
        api.setFinishedAt(Instant.now());
        persist(api);
    }

    public void updateProgress(GeneratedApi api, int progress) {
        GeneratedApi managed = repo.findById(api.getId()).orElseThrow();
        int safeProgress = Math.max(0, Math.min(progress, 100));
        if (managed.getProgress() != null && managed.getProgress() == safeProgress) {
            return;
        }
        managed.setProgress(safeProgress);
        persist(managed);
    }

    public void appendLog(GeneratedApi api, String log) {
        GeneratedApi managed = repo.findById(api.getId()).orElseThrow();
        String existing = managed.getLogs() == null ? "" : managed.getLogs();
        String entry = Instant.now() + " - " + (log == null ? "" : log.strip());
        managed.setLogs(existing.isBlank() ? entry : existing + "\n" + entry);
        persist(managed);
    }

    public List<GeneratedApi> findByUser(AppUser user) {
        return repo.findByUserOrderByCreatedAtDesc(user);
    }

    public GeneratedApi findOwnedByJobIdOrNull(String jobId, String userEmail) {
        if (jobId == null || jobId.isBlank() || userEmail == null || userEmail.isBlank()) {
            return null;
        }
        return repo.findByJobIdAndUser_EmailIgnoreCase(jobId, userEmail).orElse(null);
    }

    public GeneratedApi requireOwned(UUID id, AppUser user) {
        return repo.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Generated API not found"));
    }

    public boolean markZipDownloadedIfFirst(UUID id, AppUser user) {
        GeneratedApi api = requireOwned(id, user);
        if (api.getZipDownloadedAt() != null) {
            return false;
        }
        api.setZipDownloadedAt(Instant.now());
        persist(api);
        return true;
    }

    public void markZipDownloadedIfFirst(String jobId, String userEmail) {
        repo.findByJobIdAndUser_EmailIgnoreCase(jobId, userEmail)
                .ifPresent(api -> {
                    if (api.getZipDownloadedAt() != null) {
                        return;
                    }
                    api.setZipDownloadedAt(Instant.now());
                    persist(api);
                });
    }

    @Async
    public void watchJob(UUID generatedApiId, String jobId) {
        GeneratedApi generatedApi = repo.findById(generatedApiId).orElseThrow();
        JobStatus previousStatus = null;
        int mirroredUserLogs = 0;

        try {
            while (true) {
                JobInfo job = jobService.getJob(jobId).orElse(null);
                if (job == null) {
                    appendLog(generatedApi, "ERROR: " + MISSING_JOB_MESSAGE);
                    markFailed(generatedApiId, MISSING_JOB_MESSAGE);
                    return;
                }

                List<String> userLogs = jobService.getLogs(jobId, Integer.MAX_VALUE, true);
                for (int i = mirroredUserLogs; i < userLogs.size(); i++) {
                    ProgressLog progressLog = toProgressLog(userLogs.get(i));
                    appendLog(generatedApi, progressLog.message());
                    if (progressLog.progress() != null) {
                        updateProgress(generatedApi, progressLog.progress());
                    }
                }
                mirroredUserLogs = userLogs.size();

                if (job.status() != previousStatus) {
                    Integer progress = progressFor(job.status());
                    if (progress != null) {
                        updateProgress(generatedApi, progress);
                    }
                    previousStatus = job.status();
                }

                switch (job.status()) {
                    case SUCCEEDED, DEPLOYED, STOPPED -> {
                        String path = job.zipPath() == null ? null : job.zipPath().toString();
                        markDone(generatedApiId, path, job.apiBaseUrl());
                        return;
                    }
                    case FAILED -> {
                        markFailed(generatedApiId, job.error());
                        return;
                    }
                    default -> {
                        LockSupport.parkNanos(WATCH_POLL_MS * 1_000_000L);
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("Generation watcher interrupted");
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Generation watcher interrupted for {}", generatedApiId, e);
            appendLog(generatedApi, "ERROR: Generation tracking was interrupted.");
            markFailed(generatedApiId, "Generation tracking was interrupted.");
        } catch (Exception e) {
            log.error("Unable to track generation {}", generatedApiId, e);
            appendLog(generatedApi, "ERROR: " + safeText(e.getMessage()));
            markFailed(generatedApiId, e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.generation.jobs.generated-api-reconcile-delay-ms:1500}")
    public void reconcilePendingGeneratedApis() {
        repo.findTop20ByStatusAndJobIdIsNotNullOrderByCreatedAtAsc(GenerationStatus.PENDING)
                .forEach(api -> reconcilePendingGeneratedApi(api.getId(), api.getJobId()));
    }

    void reconcilePendingGeneratedApi(UUID generatedApiId, String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }

        GeneratedApi generatedApi = repo.findById(generatedApiId).orElse(null);
        if (generatedApi == null || generatedApi.getStatus() != GenerationStatus.PENDING) {
            return;
        }

        JobInfo job = jobService.getJob(jobId).orElse(null);
        if (job == null) {
            return;
        }

        int mirroredLogs = mirrorPersistedUserLogs(generatedApi, jobId);
        Integer progress = progressFor(job.status());
        if (progress != null) {
            updateProgress(generatedApi, progress);
        }

        switch (job.status()) {
            case SUCCEEDED, DEPLOYED, STOPPED -> {
                if (mirroredLogs == 0) {
                    appendIfMissing(generatedApi, "Generation synchronized from persisted job state.");
                }
                String path = job.zipPath() == null ? null : job.zipPath().toString();
                markDone(generatedApiId, path, job.apiBaseUrl());
            }
            case FAILED -> {
                appendIfMissing(generatedApi, "ERROR: " + safeText(job.error()));
                markFailed(generatedApiId, job.error());
            }
            default -> {
                // Keep waiting for a terminal technical status.
            }
        }
    }

    private int mirrorPersistedUserLogs(GeneratedApi generatedApi, String jobId) {
        List<String> userLogs = jobService.getLogs(jobId, Integer.MAX_VALUE, true);
        if (userLogs == null || userLogs.isEmpty()) {
            return 0;
        }

        int mirrored = 0;
        for (String userLog : userLogs) {
            ProgressLog progressLog = toProgressLog(userLog);
            appendIfMissing(generatedApi, progressLog.message());
            if (progressLog.progress() != null) {
                updateProgress(generatedApi, progressLog.progress());
            }
            mirrored++;
        }
        return mirrored;
    }

    private Integer progressFor(JobStatus status) {
        return switch (status) {
            case PENDING -> 5;
            case RUNNING -> 20;
            case BUILDING -> 85;
            case DOCKER_BUILDING -> 95;
            case SUCCEEDED, DEPLOYED, STOPPED -> 100;
            case FAILED -> null;
        };
    }

    private ProgressLog toProgressLog(String rawLine) {
        String[] parts = rawLine == null ? new String[0] : rawLine.split("\\|", 2);
        String code = parts.length == 0 ? "" : parts[0];
        String detail = parts.length > 1 ? parts[1] : "";

        return switch (code) {
            case "generation.started" -> new ProgressLog("Start generation", 10);
            case "generation.templateReady" -> new ProgressLog("Project template ready", 25);
            case "generation.configurationReady" -> new ProgressLog("Reading YAML / config", 35);
            case "generation.schemaReady" -> new ProgressLog("Generating entities and schema metadata", 55);
            case "generation.dockerFilesReady" -> new ProgressLog("Generating repositories and deployment files", 65);
            case "generation.zipReady" -> new ProgressLog("Packaging ZIP", 80);
            case "generation.building" -> new ProgressLog("Generating services and building project", 88);
            case "generation.buildReady" -> new ProgressLog("Build complete", 92);
            case "generation.deploying" -> new ProgressLog("Generating controllers and deploying Docker image", 95);
            case "generation.portFallback" -> new ProgressLog(formatPortFallback(detail), 95);
            case "generation.deployed" -> new ProgressLog("API deployed: " + safeText(detail), 100);
            case "generation.done" -> new ProgressLog("DONE", 100);
            case "generation.failed" -> new ProgressLog("ERROR: " + safeText(detail), null);
            default -> new ProgressLog(toSentence(rawLine), null);
        };
    }

    private String formatPortFallback(String detail) {
        String[] parts = safeText(detail).split("->", 2);
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            return "Docker host port " + parts[0].trim() + " is already in use; using " + parts[1].trim() + " instead";
        }
        return "Preferred Docker host port is unavailable; using another free port";
    }

    private String toSentence(String rawLine) {
        String text = safeText(rawLine).replace('|', ' ').replace('_', ' ').trim();
        if (text.isBlank()) {
            return "Generation update";
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private void appendIfMissing(GeneratedApi api, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        GeneratedApi managed = repo.findById(api.getId()).orElseThrow();
        String existing = managed.getLogs() == null ? "" : managed.getLogs();
        if (existing.contains(line)) {
            return;
        }
        appendLog(managed, line);
    }

    @SuppressWarnings("UnusedReturnValue")
    private GeneratedApi persist(GeneratedApi api) {
        return repo.save(api);
    }

    private record ProgressLog(String message, Integer progress) {
    }
}
