package com.api.generator.account.api;

import com.api.generator.account.ApiPreview;
import com.api.generator.account.AppUser;
import com.api.generator.account.GeneratedApi;
import com.api.generator.account.GenerationStatus;
import com.api.generator.account.PreviewStatus;
import com.api.generator.account.repo.ApiPreviewRepository;
import com.api.generator.account.repo.AppUserRepository;
import com.api.generator.account.service.AccountService;
import com.api.generator.account.service.PlanCapabilityService;
import com.api.generator.api.service.GenerationService;
import com.api.generator.api.service.PreviewService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
@RequestMapping(path = "/api/account", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountController {

    private final AppUserRepository users;
    private final GenerationService generations;
    private final PreviewService previews;
    private final ApiPreviewRepository previewRepo;
    private final AccountService accountService;
    private final PlanCapabilityService planCapabilityService;
    public AccountController(AppUserRepository users,
                             GenerationService generations,
                             PreviewService previews,
                             ApiPreviewRepository previewRepo,
                             AccountService accountService,
                             PlanCapabilityService planCapabilityService) {
        this.users = users;
        this.generations = generations;
        this.previews = previews;
        this.previewRepo = previewRepo;
        this.accountService = accountService;
        this.planCapabilityService = planCapabilityService;
    }

    @GetMapping("/apis")
    public List<GeneratedApiView> myApis(Authentication auth) {
        AppUser user = requireUser(auth);
        return generations.findByUser(user).stream()
                .map(GeneratedApiView::from)
                .toList();
    }

    @GetMapping("/previews/failed")
    public List<FailedPreviewView> failedPreviews(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "5") int limit,
                                                  Authentication auth) {
        AppUser user = requireUser(auth);
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return previewRepo.findByGeneratedApi_User_IdAndStatusOrderByStoppedAtDesc(
                        user.getId(),
                        PreviewStatus.FAILED,
                        PageRequest.of(0, safeLimit)
                ).stream()
                .map(FailedPreviewView::from)
                .toList();
    }

    @GetMapping("/apis/{id}")
    public GeneratedApiDetailView get(@PathVariable UUID id, Authentication auth) {
        AppUser user = requireUser(auth);
        return GeneratedApiDetailView.from(generations.requireOwned(id, user));
    }

    @GetMapping("/summary")
    public AccountSummaryView summary(Authentication auth) {
        AppUser user = requireUser(auth);
        List<GeneratedApi> apis = generations.findByUser(user);

        long completed = apis.stream().filter(api -> api.getStatus() == GenerationStatus.DONE).count();
        long failed = apis.stream().filter(api -> api.getStatus() == GenerationStatus.FAILED).count();
        long averageGenerationSeconds = Math.round(apis.stream()
                .filter(api -> api.getCreatedAt() != null && api.getFinishedAt() != null)
                .mapToLong(api -> Duration.between(api.getCreatedAt(), api.getFinishedAt()).getSeconds())
                .average()
                .orElse(0));
        List<ApiPreview> previewHistory = previewRepo.findAllByGeneratedApi_User_Id(user.getId());
        long previewsStarted = previewHistory.stream().filter(preview -> preview.getStartedAt() != null).count();
        long failedPreviews = previewHistory.stream().filter(preview -> preview.getStatus() == PreviewStatus.FAILED).count();
        long averagePreviewStartupSeconds = Math.round(previewHistory.stream()
                .filter(preview -> preview.getCreatedAt() != null && preview.getStartedAt() != null)
                .mapToLong(preview -> Duration.between(preview.getCreatedAt(), preview.getStartedAt()).getSeconds())
                .average()
                .orElse(0));
        long averagePreviewRuntimeSeconds = Math.round(previewHistory.stream()
                .filter(preview -> preview.getStartedAt() != null && preview.getStoppedAt() != null)
                .mapToLong(preview -> Duration.between(preview.getStartedAt(), preview.getStoppedAt()).getSeconds())
                .average()
                .orElse(0));

        return new AccountSummaryView(
                apis.size(),
                completed,
                failed,
                averageGenerationSeconds,
                previewRepo.countByGeneratedApi_User_IdAndStatusIn(
                        user.getId(),
                        List.of(PreviewStatus.STARTING, PreviewStatus.RUNNING, PreviewStatus.STOPPING)
                ),
                previewRepo.countByGeneratedApi_User_IdAndStatus(user.getId(), PreviewStatus.RUNNING),
                previewsStarted,
                failedPreviews,
                averagePreviewStartupSeconds,
                averagePreviewRuntimeSeconds
        );
    }

    @GetMapping("/apis/{id}/preview")
    public ApiPreviewView preview(@PathVariable UUID id, Authentication auth) {
        AppUser user = requireUser(auth);
        return ApiPreviewView.from(previews.getByGeneratedApi(generations.requireOwned(id, user)), id);
    }

    @GetMapping("/apis/{id}/preview/diagnostics")
    public PreviewDiagnosticsView previewDiagnostics(@PathVariable UUID id, Authentication auth) {
        AppUser user = requireUser(auth);
        GeneratedApi generatedApi = generations.requireOwned(id, user);
        return PreviewDiagnosticsView.from(previews.diagnostics(generatedApi));
    }

    @PostMapping("/apis/{id}/preview/start")
    public ApiPreviewView startPreview(@PathVariable UUID id, Authentication auth) {
        AppUser user = requireUser(auth);
        GeneratedApi generatedApi = generations.requireOwned(id, user);
        ApiPreview preview = previews.requestStart(generatedApi);
        previews.startAsync(generatedApi.getId());
        return ApiPreviewView.from(preview, id);
    }

    @PostMapping("/apis/{id}/preview/stop")
    public ApiPreviewView stopPreview(@PathVariable UUID id, Authentication auth) {
        AppUser user = requireUser(auth);
        GeneratedApi generatedApi = generations.requireOwned(id, user);
        return ApiPreviewView.from(previews.stop(generatedApi), id);
    }

    @PostMapping("/apis/{id}/preview/restart")
    public ApiPreviewView restartPreview(@PathVariable UUID id, Authentication auth) {
        AppUser user = requireUser(auth);
        GeneratedApi generatedApi = generations.requireOwned(id, user);
        ApiPreview preview = previews.restart(generatedApi);
        previews.startAsync(generatedApi.getId());
        return ApiPreviewView.from(preview, id);
    }

    @GetMapping("/apis/{id}/preview/logs")
    public List<String> previewLogs(@PathVariable UUID id,
                                    @org.springframework.web.bind.annotation.RequestParam(defaultValue = "200") int tail,
                                    Authentication auth) {
        AppUser user = requireUser(auth);
        GeneratedApi generatedApi = generations.requireOwned(id, user);
        return previews.logs(generatedApi, Math.min(Math.max(tail, 1), 500));
    }

    @GetMapping(value = "/apis/{id}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<FileSystemResource> download(@PathVariable UUID id, Authentication auth) {
        AppUser user = requireUser(auth);
        GeneratedApi api = generations.requireOwned(id, user);
        planCapabilityService.ensureCanDownloadZip(user);

        if (api.getFilePath() == null || api.getFilePath().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Generated ZIP is not available");
        }

        Path file = Path.of(api.getFilePath());
        if (!Files.exists(file)) {
            throw new ResponseStatusException(BAD_REQUEST, "Generated ZIP is not available");
        }

        boolean firstDownload = generations.markZipDownloadedIfFirst(id, user);
        if (!firstDownload) {
            throw new ResponseStatusException(CONFLICT, "ZIP already downloaded for this generation");
        }
        accountService.incrementMonthlyZipDownload(user.getId(), planCapabilityService);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + buildFileName(api) + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }

    private AppUser requireUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
    }

    private String buildFileName(GeneratedApi api) {
        String baseName = api.getName() == null ? "generated-api" : api.getName().trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (baseName.isBlank()) {
            baseName = "generated-api";
        }
        return baseName + ".zip";
    }

    public record GeneratedApiView(
            UUID id,
            String name,
            String status,
            Integer progress,
            String jobId,
            String dbType,
            String downloadUrl,
            String apiBaseUrl,
            String proxyUrl,
            String errorMessage,
            String createdAt,
            String finishedAt,
            String zipDownloadedAt
    ) {
        static GeneratedApiView from(GeneratedApi api) {
            String proxyUrl = "/api/account/apis/" + api.getId() + "/proxy";
            return new GeneratedApiView(
                    api.getId(),
                    api.getName(),
                    api.getStatus() == null ? null : api.getStatus().name(),
                    api.getProgress(),
                    api.getJobId(),
                    api.getDbType(),
                    api.getFilePath() == null || api.getFilePath().isBlank() ? null : "/api/account/apis/" + api.getId() + "/download",
                    api.getApiBaseUrl(),
                    proxyUrl,
                    api.getErrorMessage(),
                    api.getCreatedAt() == null ? null : api.getCreatedAt().toString(),
                    api.getFinishedAt() == null ? null : api.getFinishedAt().toString(),
                    api.getZipDownloadedAt() == null ? null : api.getZipDownloadedAt().toString()
            );
        }
    }

    public record GeneratedApiDetailView(
            UUID id,
            String name,
            String status,
            Integer progress,
            String jobId,
            String dbType,
            String downloadUrl,
            String apiBaseUrl,
            String proxyUrl,
            String errorMessage,
            String logs,
            String createdAt,
            String finishedAt,
            String zipDownloadedAt
    ) {
        static GeneratedApiDetailView from(GeneratedApi api) {
            String proxyUrl = "/api/account/apis/" + api.getId() + "/proxy";
            return new GeneratedApiDetailView(
                    api.getId(),
                    api.getName(),
                    api.getStatus() == null ? null : api.getStatus().name(),
                    api.getProgress(),
                    api.getJobId(),
                    api.getDbType(),
                    api.getFilePath() == null || api.getFilePath().isBlank() ? null : "/api/account/apis/" + api.getId() + "/download",
                    api.getApiBaseUrl(),
                    proxyUrl,
                    api.getErrorMessage(),
                    api.getLogs(),
                    api.getCreatedAt() == null ? null : api.getCreatedAt().toString(),
                    api.getFinishedAt() == null ? null : api.getFinishedAt().toString(),
                    api.getZipDownloadedAt() == null ? null : api.getZipDownloadedAt().toString()
            );
        }
    }

    public record ApiPreviewView(
            UUID id,
            String status,
            Integer hostPort,
            String baseUrl,
            String proxyUrl,
            String errorCode,
            String errorMessage,
            String errorHint,
            String createdAt,
            String startedAt,
            String stoppedAt
    ) {
        static ApiPreviewView from(ApiPreview preview, UUID generatedApiId) {
            return new ApiPreviewView(
                    preview.getId(),
                    preview.getStatus() == null ? null : preview.getStatus().name(),
                    preview.getHostPort(),
                    preview.getBaseUrl(),
                    "/api/account/apis/" + generatedApiId + "/preview/proxy",
                    preview.getErrorCode(),
                    preview.getErrorMessage(),
                    preview.getErrorHint(),
                    preview.getCreatedAt() == null ? null : preview.getCreatedAt().toString(),
                    preview.getStartedAt() == null ? null : preview.getStartedAt().toString(),
                    preview.getStoppedAt() == null ? null : preview.getStoppedAt().toString()
            );
        }
    }

    public record PreviewDiagnosticsView(
            String generationStatus,
            String previewStatus,
            boolean generationDone,
            boolean previewConfigAvailable,
            boolean zipAvailable,
            boolean hostReady,
            String containerRuntime,
            List<PreviewHostCheckView> hostChecks,
            PreviewRecommendationView recommendedAction
    ) {
        static PreviewDiagnosticsView from(PreviewService.PreviewDiagnostics diagnostics) {
            return new PreviewDiagnosticsView(
                    diagnostics.generationStatus(),
                    diagnostics.previewStatus(),
                    diagnostics.generationDone(),
                    diagnostics.previewConfigAvailable(),
                    diagnostics.zipAvailable(),
                    diagnostics.hostReady(),
                    diagnostics.containerRuntime(),
                    diagnostics.hostChecks().stream()
                            .map(check -> new PreviewHostCheckView(check.key(), check.ok(), check.details()))
                            .toList(),
                    diagnostics.recommendedAction() == null
                            ? null
                            : new PreviewRecommendationView(
                                    diagnostics.recommendedAction().code(),
                                    diagnostics.recommendedAction().message()
                            )
            );
        }
    }

    public record PreviewHostCheckView(
            String key,
            boolean ok,
            String details
    ) {
    }

    public record PreviewRecommendationView(
            String code,
            String message
    ) {
    }

    public record FailedPreviewView(
            UUID generatedApiId,
            String generatedApiName,
            String previewStatus,
            String errorCode,
            String errorMessage,
            String errorHint,
            String stoppedAt
    ) {
        static FailedPreviewView from(ApiPreview preview) {
            return new FailedPreviewView(
                    preview.getGeneratedApi().getId(),
                    preview.getGeneratedApi().getName(),
                    preview.getStatus() == null ? null : preview.getStatus().name(),
                    preview.getErrorCode(),
                    preview.getErrorMessage(),
                    preview.getErrorHint(),
                    preview.getStoppedAt() == null ? null : preview.getStoppedAt().toString()
            );
        }
    }

    public record AccountSummaryView(
            int totalGeneratedApis,
            long completedGenerations,
            long failedGenerations,
            long averageGenerationSeconds,
            long activePreviews,
            long runningPreviews,
            long previewsStarted,
            long failedPreviews,
            long averagePreviewStartupSeconds,
            long averagePreviewRuntimeSeconds
    ) {
    }
}
