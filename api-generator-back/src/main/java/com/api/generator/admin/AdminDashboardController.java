package com.api.generator.admin;

import com.api.generator.account.ApiPreview;
import com.api.generator.account.GeneratedApi;
import com.api.generator.account.GenerationStatus;
import com.api.generator.account.PreviewStatus;
import com.api.generator.account.repo.ApiPreviewRepository;
import com.api.generator.account.repo.AppUserRepository;
import com.api.generator.account.repo.GeneratedApiRepository;
import com.api.generator.api.persistence.GenerationJobRecord;
import com.api.generator.api.persistence.GenerationJobRecordRepository;
import com.api.generator.api.service.JobStatus;
import com.api.generator.config.GenerationJobProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping(path = "/api/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminDashboardController {

    private static final int MAX_LIMIT = 50;
    private static final int ERROR_FETCH_MULTIPLIER = 2;
    private static final int MAX_TIMING_SAMPLE = 1000;
    private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b([\\w.-]*(?:password|passwd|pwd|token|secret)|api[_-]?key|key|client[_-]?secret|access[_-]?token|refresh[_-]?token|jwt[_-]?secret|bootstrap[_-]?password(?:[_-]?hash)?)\\b\\s*([=:])\\s*(\"[^\"]*\"|'[^']*'|[^\\s&,}]+)"
    );
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
            "(?i)\\b(authorization)\\s*:\\s*(bearer|basic)\\s+[^\\s,}]+"
    );
    private static final List<JobStatus> ACTIVE_JOB_STATUSES = List.of(
            JobStatus.PENDING,
            JobStatus.RUNNING,
            JobStatus.BUILDING,
            JobStatus.DOCKER_BUILDING
    );

    private static final List<PreviewStatus> ACTIVE_PREVIEW_STATUSES = List.of(
            PreviewStatus.STARTING,
            PreviewStatus.RUNNING,
            PreviewStatus.STOPPING
    );

    private final AppUserRepository users;
    private final GeneratedApiRepository generatedApis;
    private final ApiPreviewRepository previews;
    private final GenerationJobRecordRepository jobs;
    private final ApiRequestAuditService apiRequestAuditService;
    private final GenerationJobProperties generationJobProperties;

    public AdminDashboardController(AppUserRepository users,
                                    GeneratedApiRepository generatedApis,
                                    ApiPreviewRepository previews,
                                    GenerationJobRecordRepository jobs,
                                    ApiRequestAuditService apiRequestAuditService,
                                    GenerationJobProperties generationJobProperties) {
        this.users = users;
        this.generatedApis = generatedApis;
        this.previews = previews;
        this.jobs = jobs;
        this.apiRequestAuditService = apiRequestAuditService;
        this.generationJobProperties = generationJobProperties;
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public AdminDashboardView dashboard(@RequestParam(defaultValue = "12") int limit) {
        int safeLimit = clamp(limit, 1, MAX_LIMIT);
        Instant now = Instant.now();
        Instant last24Hours = now.minus(Duration.ofHours(24));

        List<GenerationJobRecord> recentJobs = jobs.findAll(
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
        Map<String, GeneratedApi> apisByJobId = findApisByJobId(recentJobs);
        Map<String, GenerationJobRecord> jobsById = recentJobs.stream()
                .filter(job -> hasText(job.getJobId()))
                .collect(Collectors.toMap(GenerationJobRecord::getJobId, job -> job, (first, second) -> first));

        long totalGeneratedApis = generatedApis.count();
        long successfulGenerations = generatedApis.countByStatus(GenerationStatus.DONE);
        long failedGenerations = generatedApis.countByStatus(GenerationStatus.FAILED);
        long generationAttempts = jobs.count();
        long activeJobs = jobs.countByStatusIn(ACTIVE_JOB_STATUSES);
        long totalPreviews = previews.count();
        long activePreviews = previews.countByStatusIn(ACTIVE_PREVIEW_STATUSES);
        long failedPreviews = previews.countByStatus(PreviewStatus.FAILED);
        long averageGenerationSeconds = averageGenerationSeconds();
        long attemptsLast24h = jobs.countByCreatedAtAfter(last24Hours);
        long apisCreatedLast24h = generatedApis.countByCreatedAtAfter(last24Hours);
        long previewsCreatedLast24h = previews.countByCreatedAtAfter(last24Hours);

        AdminDashboardSummaryView summary = new AdminDashboardSummaryView(
                users.count(),
                totalGeneratedApis,
                activeJobs,
                generationAttempts,
                successfulGenerations,
                failedGenerations,
                totalPreviews,
                activePreviews,
                failedPreviews,
                averageGenerationSeconds,
                successRate(totalGeneratedApis, successfulGenerations),
                failureRate(totalGeneratedApis, failedGenerations),
                attemptsLast24h,
                apisCreatedLast24h,
                previewsCreatedLast24h,
                now.toString()
        );

        List<AdminGeneratedApiView> recentApis = generatedApis.findByOrderByCreatedAtDesc(
                        PageRequest.of(0, safeLimit)
                ).stream()
                .map(api -> AdminGeneratedApiView.from(api, jobsById.get(api.getJobId())))
                .toList();

        List<AdminJobAttemptView> jobAttempts = recentJobs.stream()
                .map(job -> AdminJobAttemptView.from(job, apisByJobId.get(job.getJobId())))
                .toList();

        List<AdminErrorView> errors = recentErrors(safeLimit);

        return new AdminDashboardView(
                summary,
                generationStatusCounts(),
                jobStatusCounts(),
                previewStatusCounts(),
                recentApis,
                jobAttempts,
                errors,
                recentApiCalls(safeLimit),
                databaseTool()
        );
    }

    private long averageGenerationSeconds() {
        return Math.round(generatedApis.findRecentFinishedGenerationTimings(PageRequest.of(0, MAX_TIMING_SAMPLE)).stream()
                .mapToLong(timing -> Duration.between(timing.getCreatedAt(), timing.getFinishedAt()).getSeconds())
                .average()
                .orElse(0));
    }

    private List<AdminApiCallView> recentApiCalls(int safeLimit) {
        return apiRequestAuditService.recent(safeLimit).stream()
                .map(AdminApiCallView::from)
                .toList();
    }

    private AdminDatabaseToolView databaseTool() {
        int port = generationJobProperties.getCloudbeaverPort();
        String host = generationJobProperties.getDockerBaseUrlHost();
        if (!hasText(host)) {
            host = "localhost";
        }
        return new AdminDatabaseToolView(
                "CloudBeaver",
                port > 0,
                "http://" + host + ":" + port,
                "Admin SQL console for local/generated databases. Keep it behind admin access or a private network in production."
        );
    }

    private Map<String, GeneratedApi> findApisByJobId(List<GenerationJobRecord> recentJobs) {
        List<String> jobIds = recentJobs.stream()
                .map(GenerationJobRecord::getJobId)
                .filter(AdminDashboardController::hasText)
                .distinct()
                .toList();
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        return generatedApis.findByJobIdIn(jobIds).stream()
                .filter(api -> hasText(api.getJobId()))
                .collect(Collectors.toMap(GeneratedApi::getJobId, api -> api, (first, second) -> first));
    }

    private List<AdminErrorView> recentErrors(int safeLimit) {
        PageRequest errorPage = PageRequest.of(0, Math.min(safeLimit * ERROR_FETCH_MULTIPLIER, MAX_LIMIT));
        List<AdminErrorView> generationErrors = generatedApis.findRecentErroredWithUser(errorPage).stream()
                .map(AdminErrorView::fromGeneration)
                .toList();
        List<AdminErrorView> previewErrors = previews.findRecentErroredWithApiAndUser(errorPage).stream()
                .map(AdminErrorView::fromPreview)
                .toList();

        return Stream.concat(generationErrors.stream(), previewErrors.stream())
                .sorted(Comparator.comparing(AdminErrorView::occurredAtInstant, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .toList();
    }

    private Map<String, Long> generationStatusCounts() {
        Map<String, Long> counts = enumCountMap(GenerationStatus.values());
        generatedApis.countByStatusGroup().forEach(row -> counts.put(normalizeStatus(row.getStatus()), row.getTotal()));
        return counts;
    }

    private Map<String, Long> jobStatusCounts() {
        Map<String, Long> counts = enumCountMap(JobStatus.values());
        jobs.countByStatusGroup().forEach(row -> counts.put(normalizeStatus(row.getStatus()), row.getTotal()));
        return counts;
    }

    private Map<String, Long> previewStatusCounts() {
        Map<String, Long> counts = enumCountMap(PreviewStatus.values());
        previews.countByStatusGroup().forEach(row -> counts.put(normalizeStatus(row.getStatus()), row.getTotal()));
        return counts;
    }

    private static <E extends Enum<E>> Map<String, Long> enumCountMap(E[] values) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (E value : values) {
            counts.put(value.name(), 0L);
        }
        return counts;
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static long successRate(long total, long success) {
        return percentage(success, total);
    }

    private static long failureRate(long total, long failures) {
        return percentage(failures, total);
    }

    private static long percentage(long part, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.round((part * 100.0) / total);
    }

    private static String normalizeStatus(Enum<?> status) {
        return status == null ? "UNKNOWN" : status.name();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static String redactSensitive(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = AUTHORIZATION_PATTERN.matcher(value).replaceAll("$1: $2 ***");
        Matcher matcher = SECRET_ASSIGNMENT_PATTERN.matcher(redacted);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String rawSecret = matcher.group(3);
            String replacementSecret = quotedMask(rawSecret);
            String separator = ":".equals(matcher.group(2)) ? ": " : "=";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + separator + replacementSecret
            ));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String quotedMask(String rawSecret) {
        if (rawSecret != null && rawSecret.length() >= 2) {
            char first = rawSecret.charAt(0);
            char last = rawSecret.charAt(rawSecret.length() - 1);
            if ((first == '\"' && last == '\"') || (first == '\'' && last == '\'')) {
                return first + "***" + last;
            }
        }
        return "***";
    }

    public record AdminDashboardView(
            AdminDashboardSummaryView summary,
            Map<String, Long> generationStatuses,
            Map<String, Long> jobStatuses,
            Map<String, Long> previewStatuses,
            List<AdminGeneratedApiView> recentApis,
            List<AdminJobAttemptView> jobAttempts,
            List<AdminErrorView> errors,
            List<AdminApiCallView> recentApiCalls,
            AdminDatabaseToolView databaseTool
    ) {
    }

    public record AdminDashboardSummaryView(
            long totalUsers,
            long totalGeneratedApis,
            long activeJobs,
            long generationAttempts,
            long successfulGenerations,
            long failedGenerations,
            long totalPreviews,
            long activePreviews,
            long failedPreviews,
            long averageGenerationSeconds,
            long successRate,
            long failureRate,
            long attemptsLast24h,
            long apisCreatedLast24h,
            long previewsCreatedLast24h,
            String generatedAt
    ) {
    }

    public record AdminApiCallView(
            String timestamp,
            String traceId,
            String method,
            String path,
            int status,
            long durationMs,
            String principal,
            String clientIp
    ) {
        static AdminApiCallView from(ApiRequestAuditService.ApiRequestAuditEvent event) {
            return new AdminApiCallView(
                    iso(event.timestamp()),
                    event.traceId(),
                    event.method(),
                    event.path(),
                    event.status(),
                    event.durationMs(),
                    event.principal(),
                    event.clientIp()
            );
        }
    }

    public record AdminDatabaseToolView(
            String name,
            boolean enabled,
            String url,
            String warning
    ) {
    }

    public record AdminGeneratedApiView(
            UUID id,
            String name,
            String ownerEmail,
            String status,
            Integer progress,
            String jobId,
            String jobStatus,
            String dbType,
            String errorMessage,
            String createdAt,
            String finishedAt
    ) {
        static AdminGeneratedApiView from(GeneratedApi api, GenerationJobRecord job) {
            return new AdminGeneratedApiView(
                    api.getId(),
                    api.getName(),
                    api.getUser().getEmail(),
                    normalizeStatus(api.getStatus()),
                    api.getProgress(),
                    api.getJobId(),
                    job == null ? null : normalizeStatus(job.getStatus()),
                    api.getDbType(),
                    redactSensitive(api.getErrorMessage()),
                    iso(api.getCreatedAt()),
                    iso(api.getFinishedAt())
            );
        }
    }

    public record AdminJobAttemptView(
            String jobId,
            String generatedApiId,
            String generatedApiName,
            String ownerEmail,
            String status,
            boolean buildRequested,
            boolean deployDockerRequested,
            Integer preferredPort,
            Integer hostPort,
            String errorMessage,
            String createdAt,
            String updatedAt
    ) {
        static AdminJobAttemptView from(GenerationJobRecord job, GeneratedApi api) {
            return new AdminJobAttemptView(
                    job.getJobId(),
                    api == null ? null : api.getId().toString(),
                    api == null ? null : api.getName(),
                    api == null ? null : api.getUser().getEmail(),
                    normalizeStatus(job.getStatus()),
                    job.isBuildRequested(),
                    job.isDeployDockerRequested(),
                    job.getPreferredPort(),
                    job.getHostPort(),
                    redactSensitive(job.getErrorMessage()),
                    iso(job.getCreatedAt()),
                    iso(job.getUpdatedAt())
            );
        }
    }

    public record AdminErrorView(
            String source,
            String code,
            String message,
            String hint,
            String generatedApiId,
            String generatedApiName,
            String ownerEmail,
            String occurredAt
    ) {
        static AdminErrorView fromGeneration(GeneratedApi api) {
            return new AdminErrorView(
                    "GENERATION",
                    api.getStatus() == null ? null : api.getStatus().name(),
                    redactSensitive(api.getErrorMessage()),
                    null,
                    api.getId().toString(),
                    api.getName(),
                    api.getUser().getEmail(),
                    iso(api.getFinishedAt() == null ? api.getCreatedAt() : api.getFinishedAt())
            );
        }

        static AdminErrorView fromPreview(ApiPreview preview) {
            GeneratedApi api = preview.getGeneratedApi();
            return new AdminErrorView(
                    "PREVIEW",
                    preview.getErrorCode(),
                    redactSensitive(firstNonBlank(preview.getErrorMessage(), preview.getErrorHint())),
                    redactSensitive(preview.getErrorHint()),
                    api.getId().toString(),
                    api.getName(),
                    api.getUser().getEmail(),
                    iso(firstNonNull(preview.getStoppedAt(), preview.getStartedAt(), preview.getCreatedAt()))
            );
        }

        Instant occurredAtInstant() {
            return occurredAt == null ? null : Instant.parse(occurredAt);
        }

        private static String firstNonBlank(String... values) {
            return Stream.of(values).filter(AdminDashboardController::hasText).findFirst().orElse(null);
        }

        @SafeVarargs
        private static <T> T firstNonNull(T... values) {
            return Stream.of(values).filter(Objects::nonNull).findFirst().orElse(null);
        }
    }
}
