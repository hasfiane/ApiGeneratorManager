package com.api.generator.api.service;

import com.api.generator.api.persistence.GenerationJobRecord;
import com.api.generator.api.persistence.GenerationJobRecordRepository;
import com.api.generator.config.GenerationJobProperties;
import com.api.generator.config.GeneratorProperties;
import com.api.generator.reader.SchemaReadRequest;
import com.api.generator.reader.SchemaReader;
import com.api.generator.schema.TableInfo;
import com.api.generator.util.FileUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory job manager for API generation.
 */
@Service
public class GenerationJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationJobService.class);
    private static final BCryptPasswordEncoder GENERATED_PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private final GenerationJobProperties jobProperties;
    private final SchemaReader schemaReader;
    private final GenerationJobRecordRepository jobRecordRepository;
    private final GenerationPayloadCodec payloadCodec;

    private final Map<String, JobInfo> jobs = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> jobLogs = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> jobUserLogs = new ConcurrentHashMap<>();
    private final Map<String, Integer> suppressedTechnicalLogCounts = new ConcurrentHashMap<>();
    private final Set<Integer> reservedDockerHostPorts = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean runtimeInstalled = new AtomicBoolean(false);

    public GenerationJobService(GenerationJobProperties jobProperties, SchemaReader schemaReader) {
        this(jobProperties, schemaReader, null, null);
    }

    @Autowired
    public GenerationJobService(GenerationJobProperties jobProperties,
                                SchemaReader schemaReader,
                                GenerationJobRecordRepository jobRecordRepository,
                                GenerationPayloadCodec payloadCodec) {
        this.jobProperties = jobProperties;
        this.schemaReader = schemaReader;
        this.jobRecordRepository = jobRecordRepository;
        this.payloadCodec = payloadCodec;
    }

    @PostConstruct
    void recoverInterruptedPersistedJobs() {
        LOGGER.info(
                "Generation worker polling {}. Docker requests {}. Docker execution {}.",
                jobProperties.isWorkerEnabled() ? "enabled" : "disabled",
                jobProperties.isDockerRequestEnabled() ? "enabled" : "disabled",
                jobProperties.isDockerDeploymentEnabled() ? "enabled" : "disabled"
        );
        if (jobRecordRepository == null || !jobProperties.isWorkerEnabled()) {
            return;
        }

        Instant now = Instant.now();
        jobRecordRepository.findAllByStatusIn(List.of(
                JobStatus.RUNNING,
                JobStatus.BUILDING,
                JobStatus.DOCKER_BUILDING
        )).forEach(record -> {
            record.setStatus(JobStatus.FAILED);
            record.setUpdatedAt(now);
            record.setErrorMessage("Generation interrupted by manager restart.");
            jobRecordRepository.save(record);
        });
    }

    /**
     * Starts a generation job (backward compatible).
     */
    public String startGeneration(GeneratorProperties props, boolean async) {
        return startGeneration(props, async, true, false, null);
    }

    /**
     * Starts a generation job with optional build and Docker deploy.
     */
    public String startGeneration(GeneratorProperties props, boolean async, boolean build, boolean deployDocker, Integer preferredPort) {
        String jobId = UUID.randomUUID().toString();

        Path jobDir = createJobDir(jobId);
        Path outputDir = jobDir.resolve(jobProperties.getOutputFolderName());
        Path zipPath = jobDir.resolve(jobProperties.getZipFileName());

        props.setOutputDir(outputDir.toString());
        props.setCleanOutputDir(true);

        JobInfo info = new JobInfo(jobId, JobStatus.PENDING, Instant.now(), null, zipPath, outputDir, null, null, null);
        jobs.put(jobId, info);
        jobLogs.put(jobId, new ArrayDeque<>());
        jobUserLogs.put(jobId, new ArrayDeque<>());
        persistJobSnapshot(info, props, build, deployDocker, preferredPort);
        if (deployDocker) {
            log(jobId, "Docker deployment requested with host port: "
                    + (preferredPort == null ? "auto" : preferredPort));
        }

        Runnable task = () -> runJob(jobId, props, zipPath, outputDir, build, deployDocker, preferredPort);

        if (!async) {
            task.run();
        } else if (usesPersistedWorker()) {
            LOGGER.info("[{}] queued for persisted worker polling", jobId);
        } else {
            LOGGER.info("[{}] running asynchronously without persisted worker", jobId);
            CompletableFuture.runAsync(task);
        }

        return jobId;
    }

    /**
     * Returns job info if it exists.
     */
    public Optional<JobInfo> getJob(String jobId) {
        JobInfo inMemory = jobs.get(jobId);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }
        if (jobRecordRepository == null) {
            return Optional.empty();
        }
        return jobRecordRepository.findById(jobId).map(this::toJobInfo);
    }

    /**
     * Returns the generated zip for a job, if ready.
     */
    public Optional<Path> getZipIfReady(String jobId) {
        JobInfo job = getJob(jobId).orElse(null);
        if (job == null) return Optional.empty();
        if (job.status() != JobStatus.SUCCEEDED
                && job.status() != JobStatus.DEPLOYED
                && job.status() != JobStatus.STOPPED) return Optional.empty();
        return Optional.ofNullable(job.zipPath());
    }

    /**
     * Returns buffered logs for a job (tail).
     */
    public List<String> getLogs(String jobId, int tail) {
        return getLogs(jobId, tail, false);
    }

    public List<String> getLogs(String jobId, int tail, boolean userFriendly) {
        Deque<String> queue = userFriendly ? jobUserLogs.get(jobId) : jobLogs.get(jobId);
        if (queue == null) return getPersistedLogs(jobId, tail, userFriendly);

        int requestedTail = tail <= 0 ? jobProperties.getDefaultLogTail() : tail;
        int skip = Math.max(0, queue.size() - requestedTail);
        List<String> out = new ArrayList<>(Math.min(requestedTail, queue.size()));

        int i = 0;
        for (String line : queue) {
            if (i++ < skip) continue;
            out.add(line);
        }
        return out;
    }

    /**
     * Returns the download file name for a given job id.
     */
    public String getDownloadFileName(String jobId) {
        return jobProperties.buildDownloadFileName(jobId);
    }

    /**
     * Stops docker container for the job if it exists, and marks STOPPED.
     */
    public boolean stopJob(String jobId) {
        JobInfo job = getJob(jobId).orElse(null);
        if (job == null) return false;

        var runtime = jobProperties.getContainerRuntime();

        if (job.containerId() != null && !job.containerId().isBlank()) {
            String containerId = job.containerId();
            String composePrefix = jobProperties.getComposeContainerIdPrefix();

            if (containerId.startsWith(composePrefix)) {
                String project = containerId.substring(composePrefix.length());
                log(jobId, "Stopping compose project (" + runtime.binary() + "): " + project);
                execAndStream(jobId, job.outputDir(), runtime.composeDownCmd(project));
            } else {
                log(jobId, "Stopping container (" + runtime.binary() + "): " + containerId);
                execAndStream(jobId, job.outputDir(), runtime.stopContainerCmd(containerId));
                log(jobId, "Removing container: " + containerId);
                execAndStream(jobId, job.outputDir(), runtime.rmContainerCmd(containerId));
            }
        }

        releaseDockerHostPort(job.hostPort());
        update(jobId, JobStatus.STOPPED, null, job.hostPort(), job.apiBaseUrl(), null);
        return true;
    }

    /**
     * Best-effort cleanup.
     */
    public boolean deleteJob(String jobId) {
        JobInfo job = jobs.remove(jobId);
        if (job == null) {
            job = getJob(jobId).orElse(null);
        }
        jobLogs.remove(jobId);
        jobUserLogs.remove(jobId);
        if (jobRecordRepository != null) {
            jobRecordRepository.deleteById(jobId);
        }
        if (job == null) return false;

        releaseDockerHostPort(job.hostPort());

        try {
            FileUtils.deleteDirectory(job.outputDir());
            FileUtils.deleteDirectory(job.zipPath().getParent());
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
        return true;
    }

    @Scheduled(fixedDelayString = "${app.generation.jobs.cleanup-delay-ms:3600000}")
    public void cleanupFinishedJobs() {
        if (!jobProperties.isWorkerEnabled()) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(jobProperties.getRetentionHours() * 3600);
        List<String> expired = jobs.values().stream()
                .filter(job -> job.createdAt().isBefore(cutoff))
                .filter(job -> switch (job.status()) {
                    case SUCCEEDED, DEPLOYED, FAILED, STOPPED -> true;
                    default -> false;
                })
                .map(JobInfo::jobId)
                .toList();

        expired.forEach(this::deleteJob);

        if (jobRecordRepository != null) {
            jobRecordRepository.findAllByCreatedAtBeforeAndStatusIn(cutoff, List.of(
                    JobStatus.SUCCEEDED,
                    JobStatus.DEPLOYED,
                    JobStatus.FAILED,
                    JobStatus.STOPPED
            )).stream()
                    .map(GenerationJobRecord::getJobId)
                    .filter(jobId -> !expired.contains(jobId))
                    .forEach(this::deleteJob);
        }
    }

    @Scheduled(fixedDelayString = "${app.generation.jobs.worker-poll-delay-ms:1000}")
    @Transactional
    public void pollPersistedPendingJobs() {
        if (jobRecordRepository == null || !jobProperties.isWorkerEnabled()) {
            return;
        }

        jobRecordRepository.findTop10ByStatusOrderByCreatedAtAsc(JobStatus.PENDING)
                .forEach(record -> {
                    int claimed = jobRecordRepository.updateStatusIfCurrent(
                            record.getJobId(),
                            JobStatus.PENDING,
                            JobStatus.RUNNING,
                            Instant.now()
                    );
                    if (claimed == 0) {
                        return;
                    }
                    record.setStatus(JobStatus.RUNNING);
                    runPersistedJob(record);
                });
    }

    private void runJob(String jobId,
                        GeneratorProperties props,
                        Path zipPath,
                        Path outputDir,
                        boolean build,
                        boolean deployDocker,
                        Integer preferredPort) {

        update(jobId, JobStatus.RUNNING, null, null, null, null);
        userLog(jobId, "generation.started");
        log(jobId, "Generation started");

        try {
            if (deployDocker && (props.getSecurity() == null || !props.getSecurity().isEnabled())) {
                throw new IllegalStateException("Generated API security must be enabled before Docker deployment.");
            }
            if (deployDocker
                    && props.getDb() != null
                    && props.getDb().getType() != null
                    && "H2".equalsIgnoreCase(props.getDb().getType().name())) {
                throw new IllegalStateException("H2 demo does not support Docker deployment. Use PostgreSQL or MySQL for a live container runtime.");
            }

            // ── Deliver the template ──────────────────────────────────────────
            // The template already contains runtime + core.
            // We just configure application.yml with the user's values.
            copyTemplate(outputDir);
            userLog(jobId, "generation.templateReady");
            log(jobId, "Template copied");

            writeApplicationYml(outputDir, props);
            userLog(jobId, "generation.configurationReady");
            log(jobId, "application.yml written");

            writeSchemaJson(outputDir, props);
            userLog(jobId, "generation.schemaReady");
            log(jobId, "schema.json written");

            writeGeneratedApplicationSources(outputDir, props);
            writeGeneratedProjectSupportFiles(outputDir, props);
            log(jobId, "Generated project support files written");

            if (props.getFeatures().isGenerateDocker()) {
                writeDockerDeploymentFiles(outputDir);
                userLog(jobId, "generation.dockerFilesReady");
                log(jobId, "Docker deployment files written");
            }

            if (build || deployDocker) {
                update(jobId, JobStatus.BUILDING, null, null, null, null);
                userLog(jobId, "generation.building");
                ensureRuntimeInstalled(jobId);
                String buildCmd = resolveBuildCommand(outputDir, jobId);
                log(jobId, "Build command: " + buildCmd + " clean verify");
                execAndStream(jobId, outputDir, List.of(buildCmd, "clean", "verify"));
                userLog(jobId, "generation.buildReady");
                log(jobId, "Build complete");
            }

            zipDirectory(outputDir, zipPath);
            userLog(jobId, "generation.zipReady");
            log(jobId, "ZIP created: " + zipPath.getFileName());

            if (deployDocker) {
                if (!jobProperties.isDockerDeploymentEnabled()) {
                    throw new IllegalStateException("Docker deployment is disabled on this environment.");
                }
                update(jobId, JobStatus.DOCKER_BUILDING, null, null, null, null);
                userLog(jobId, "generation.deploying");

                Integer hostPort = reserveDockerHostPort(preferredPort);
                boolean keepPortReserved = false;
                try {
                    update(jobId, JobStatus.DOCKER_BUILDING, null, hostPort, null, null);
                    int dbHostPort = hostPort + jobProperties.getDbPortOffset();
                    log(jobId, "Docker host port selected: " + hostPort);
                    if (preferredPort != null && !preferredPort.equals(hostPort)) {
                        userLog(jobId, "generation.portFallback|" + preferredPort + "->" + hostPort);
                        log(jobId, "Preferred Docker host port " + preferredPort
                                + " is unavailable; using " + hostPort + " instead");
                    }

                    String dockerDbUrl = toDockerJdbcUrl(props.getDb().getUrl());
                    String schema = props.getDb().getSchema();
                    var runtime = jobProperties.getContainerRuntime();
                    String project = jobProperties.getComposeProjectPrefix() + jobId.toLowerCase(Locale.ROOT);
                    String apiContainerName = project + "-api-1";
                    String apiContainerAlias = "apigen-runtime-" + jobId.toLowerCase(Locale.ROOT);
                    StringBuilder env = new StringBuilder();
                    env.append("API_BIND_HOST=").append(dotenv(jobProperties.getDockerBindHost())).append('\n');
                    env.append("API_PORT=").append(hostPort).append('\n');
                    env.append("API_CONTAINER_NAME=").append(dotenv(apiContainerName)).append('\n');
                    env.append("API_CONTAINER_ALIAS=").append(dotenv(apiContainerAlias)).append('\n');
                    env.append("API_MANAGER_NETWORK=").append(dotenv(jobProperties.getDockerManagerNetworkName())).append('\n');
                    env.append("DB_PORT=").append(dbHostPort).append('\n');
                    env.append("DB_URL=").append(dotenv(dockerDbUrl)).append('\n');
                    env.append("DB_USERNAME=").append(dotenv(props.getDb().getUsername())).append('\n');
                    env.append("DB_PASSWORD=").append(dotenv(props.getDb().getPassword())).append('\n');
                    env.append("DB_TYPE=").append(dotenv(props.getDb().getType().name())).append('\n');
                    env.append("SECURITY_ENABLED=").append(dotenv(String.valueOf(props.getSecurity().isEnabled()))).append('\n');
                    env.append("BOOTSTRAP_USER=").append(dotenv(props.getSecurity().getBootstrapUsername())).append('\n');
                    env.append("BOOTSTRAP_PASSWORD_HASH=").append(dotenv(bcrypt(props.getSecurity().getBootstrapPassword()))).append('\n');
                    env.append("JWT_SECRET=").append(dotenv(props.getSecurity().getJwtSecret())).append('\n');
                    env.append("JWT_ISSUER=").append(dotenv(props.getSecurity().getJwtIssuer())).append('\n');
                    env.append("JWT_EXP_SECONDS=").append(dotenv(String.valueOf(props.getSecurity().getJwtExpirationSeconds()))).append('\n');
                    if (schema != null && !schema.isBlank()) {
                        env.append("DB_SCHEMA=").append(dotenv(schema)).append('\n');
                    }
                    Files.writeString(outputDir.resolve(".env"), env.toString(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                    if (jobProperties.isDockerHostedRouteEnabled()) {
                        writeDockerManagerDeploymentFile(outputDir);
                    }

                    log(jobId, "Compose up (" + runtime.binary() + "): " + project);
                    List<String> composeFiles = jobProperties.isDockerHostedRouteEnabled()
                            ? List.of("docker-compose.yml", "docker-compose.manager.yml")
                            : List.of("docker-compose.yml");
                    execAndStream(jobId, outputDir, runtime.composeUpCmd(project, composeFiles));

                    String apiBaseUrl = jobProperties.isDockerHostedRouteEnabled()
                            ? jobProperties.buildHostedApiBaseUrl(jobId)
                            : jobProperties.buildApiBaseUrl(hostPort);
                    String containerId = jobProperties.getComposeContainerIdPrefix() + project;

                    update(jobId, JobStatus.DEPLOYED, null, hostPort, apiBaseUrl, containerId);
                    keepPortReserved = true;
                    userLog(jobId, "generation.deployed|" + apiBaseUrl);
                    log(jobId, "Deployment complete (" + runtime.binary() + "): " + apiBaseUrl);
                    log(jobId, "Database URL inside container: " + redactSensitive(dockerDbUrl));
                    log(jobId, "Swagger: " + apiBaseUrl + "/swagger-ui/index.html");
                } finally {
                    if (!keepPortReserved) {
                        releaseDockerHostPort(hostPort);
                    }
                }
            }

            if (!deployDocker) {
                update(jobId, JobStatus.SUCCEEDED, null, null, null, null);
                userLog(jobId, "generation.done");
            }

        } catch (Exception e) {
            String safeError = summarizeFailure(e);
            update(jobId, JobStatus.FAILED, safeError, null, null, null);
            userLog(jobId, "generation.failed|" + safeError);
            log(jobId, "FAILED: " + safeError);
        }
    }

    private void update(String jobId, JobStatus status, String error, Integer hostPort, String apiBaseUrl, String containerId) {
        JobInfo prev = jobs.get(jobId);
        if (prev == null) {
            prev = getJob(jobId).orElse(null);
        }
        if (prev == null) return;
        flushSuppressedTechnicalLogs(jobId);

        jobs.put(jobId, new JobInfo(
                prev.jobId(),
                status,
                prev.createdAt(),
                error,
                prev.zipPath(),
                prev.outputDir(),
                hostPort != null ? hostPort : prev.hostPort(),
                apiBaseUrl != null ? apiBaseUrl : prev.apiBaseUrl(),
                containerId != null ? containerId : prev.containerId()
        ));
        persistJobSnapshot(jobs.get(jobId), null, null, null, null);
    }

    private void log(String jobId, String line) {
        Deque<String> queue = jobLogs.computeIfAbsent(jobId, ignored -> new ArrayDeque<>());
        String safeLine = redactSensitive(line);
        if (shouldSuppressTechnicalLogLine(safeLine)) {
            suppressedTechnicalLogCounts.merge(jobId, 1, Integer::sum);
            return;
        }
        flushSuppressedTechnicalLogs(jobId, queue);
        appendLogLine(queue, safeLine);
        LOGGER.info("[{}] {}", jobId, safeLine);
        persistLogBuffers(jobId);
    }

    private void userLog(String jobId, String line) {
        Deque<String> queue = jobUserLogs.computeIfAbsent(jobId, ignored -> new ArrayDeque<>());
        appendLogLine(queue, line);
        LOGGER.info("[{}] {}", jobId, line);
        persistLogBuffers(jobId);
    }

    private void appendLogLine(Deque<String> queue, String line) {
        if (queue.size() >= jobProperties.getMaxLogLines()) {
            queue.pollFirst();
        }
        queue.addLast(line);
    }

    private void flushSuppressedTechnicalLogs(String jobId) {
        Deque<String> queue = jobLogs.computeIfAbsent(jobId, ignored -> new ArrayDeque<>());
        flushSuppressedTechnicalLogs(jobId, queue);
    }

    private void flushSuppressedTechnicalLogs(String jobId, Deque<String> queue) {
        Integer suppressedCount = suppressedTechnicalLogCounts.remove(jobId);
        if (suppressedCount == null || suppressedCount <= 0) {
            return;
        }
        String summary = "Suppressed " + suppressedCount + " Maven transfer/progress lines.";
        appendLogLine(queue, summary);
        LOGGER.info("[{}] {}", jobId, summary);
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return redactSensitive(message).replace('\n', ' ').replace('\r', ' ');
    }

    static boolean shouldSuppressTechnicalLogLine(String line) {
        String normalized = safeMessage(line);
        if (normalized.isBlank()) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return normalized.equals("[INFO]")
                || lower.startsWith("progress (")
                || lower.startsWith("downloading from ")
                || lower.startsWith("downloaded from ");
    }

    static String summarizeFailure(Throwable failure) {
        if (failure == null) {
            return "";
        }
        List<String> messages = new ArrayList<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = safeMessage(current.getMessage());
            if (!message.isBlank()) {
                messages.add(message);
            }
        }
        String joined = String.join(" | ", messages).toLowerCase(Locale.ROOT);
        if (containsCause(failure, UnknownHostException.class) || joined.contains("unknown host")) {
            return "Database host could not be resolved. Check the JDBC host name.";
        }
        if (joined.contains("the connection attempt failed")) {
            return "Database connection failed. Check the JDBC host, port, database name, credentials, and SSL settings.";
        }
        if (joined.contains("port is already allocated")
                || joined.contains("address already in use")
                || joined.contains("bind for 0.0.0.0:")) {
            String port = extractHostPortConflict(messages);
            if (port != null) {
                return "Docker host port " + port + " is already in use. Choose another port.";
            }
            return "The requested Docker host port is already in use. Choose another port.";
        }
        if (joined.contains("docker deployment is disabled on this environment")) {
            return "Docker deployment is disabled on this environment.";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            String candidate = messages.get(i);
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private static boolean containsCause(Throwable failure, Class<? extends Throwable> expectedType) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (expectedType.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static String extractHostPortConflict(List<String> messages) {
        for (String message : messages) {
            java.util.regex.Matcher bindMatcher = java.util.regex.Pattern
                    .compile("(?i)bind for (?:0\\.0\\.0\\.0|127\\.0\\.0\\.1):(\\d+)")
                    .matcher(message);
            if (bindMatcher.find()) {
                return bindMatcher.group(1);
            }
            java.util.regex.Matcher portMatcher = java.util.regex.Pattern
                    .compile("(?i)port(?:\\s+|=)(\\d{1,5})")
                    .matcher(message);
            if (portMatcher.find()) {
                return portMatcher.group(1);
            }
        }
        return null;
    }

    private static String redactSensitive(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replaceAll("(?i)(password|passwd|pwd|token|secret|key)=([^\\s&]+)", "$1=***")
                .replaceAll("(?i)(password|passwd|pwd|token|secret|key):\\s*([^\\s,}]+)", "$1: ***")
                .replaceAll("(?i)(db_url|db_username|db_password|jwt_secret|bootstrap_password_hash)=\"[^\"]*\"", "$1=\"***\"");
    }

    private void execAndStream(String jobId, Path cwd, List<String> cmd) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        List<String> recentRelevantLines = new ArrayList<>();

        try {
            Process process = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    rememberRelevantCommandLine(recentRelevantLines, line);
                    log(jobId, line);
                }
            }
            boolean finished = process.waitFor(jobProperties.getCommandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Command timed out after " + jobProperties.getCommandTimeoutSeconds() + " seconds");
            }
            int code = process.exitValue();
            if (code != 0) {
                throw new IllegalStateException(describeCommandFailure(cmd, code, recentRelevantLines));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    static String describeCommandFailure(List<String> cmd, int code, List<String> recentRelevantLines) {
        String detail = extractCommandFailureDetail(recentRelevantLines);
        String command = String.join(" ", cmd);
        if (detail == null || detail.isBlank()) {
            return "Command failed (" + code + "): " + command;
        }
        if (!cmd.isEmpty() && cmd.get(0).toLowerCase(Locale.ROOT).contains("mvn")) {
            return "Build failed: " + detail;
        }
        return "Command failed (" + code + "): " + detail;
    }

    private static void rememberRelevantCommandLine(List<String> recentRelevantLines, String line) {
        String normalized = safeMessage(line);
        if (shouldSuppressTechnicalLogLine(normalized)) {
            return;
        }
        recentRelevantLines.add(normalized);
        if (recentRelevantLines.size() > 12) {
            recentRelevantLines.remove(0);
        }
    }

    private static String extractCommandFailureDetail(List<String> recentRelevantLines) {
        for (int i = recentRelevantLines.size() - 1; i >= 0; i--) {
            String line = recentRelevantLines.get(i);
            String cleaned = line.replaceFirst("^\\[ERROR\\]\\s*", "").trim();
            String lower = cleaned.toLowerCase(Locale.ROOT);
            if (cleaned.isBlank()
                    || cleaned.equalsIgnoreCase("BUILD FAILURE")
                    || lower.startsWith("failed to execute goal ")
                    || lower.equals("compilation failure")
                    || lower.equals("compilation error")) {
                continue;
            }
            if (line.startsWith("[ERROR]") || lower.contains("exception") || lower.contains("failed")) {
                return cleaned;
            }
        }
        for (int i = recentRelevantLines.size() - 1; i >= 0; i--) {
            String cleaned = recentRelevantLines.get(i).replaceFirst("^\\[INFO\\]\\s*", "").trim();
            if (!cleaned.isBlank()
                    && !cleaned.equalsIgnoreCase("BUILD FAILURE")
                    && !cleaned.startsWith("---")) {
                return cleaned;
            }
        }
        return null;
    }

    /**
     * Resolves the Maven build command.
     * Priority: mvnw in generated project → copy from workspace → system mvn
     */
    private String resolveBuildCommand(Path generatedProjectDir, String jobId) {
        String wrapperName = isWindows() ? "mvnw.cmd" : "mvnw";

        // 1. Already present in generated project
        if (Files.exists(generatedProjectDir.resolve(wrapperName))) {
            return isWindows() ? wrapperName : "./" + wrapperName;
        }

        // 2. Copy from workspace (cwd or ApiGeneratorManager/ sub-folder)
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
            cwd.resolve(wrapperName),
            cwd.resolve("ApiGeneratorManager").resolve(wrapperName)
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                try {
                    Path dest = generatedProjectDir.resolve(wrapperName);
                    Files.copy(candidate, dest, StandardCopyOption.REPLACE_EXISTING);
                    if (!dest.toFile().setExecutable(true)) {
                        log(jobId, "Could not mark Maven Wrapper executable: " + dest);
                    }
                    Path mvnDir = candidate.getParent().resolve(".mvn");
                    if (Files.exists(mvnDir)) {
                        FileUtils.copyDirectory(mvnDir, generatedProjectDir.resolve(".mvn"));
                    }
                    log(jobId, "Copied Maven Wrapper from: " + candidate);
                    return isWindows() ? wrapperName : "./" + wrapperName;
                } catch (IOException e) {
                    log(jobId, "Could not copy mvnw: " + e.getMessage());
                }
            }
        }

        // 3. Fallback: plain mvn from PATH
        log(jobId, "No mvnw found — using system mvn from PATH.");
        return "mvn";
    }

    /**
     * Installs api-generator-core + runtime into the local Maven repo
     * so generated projects can resolve them during build.
     */
    private void ensureRuntimeInstalled(String jobId) {
        if (runtimeInstalled.get()) {
            return;
        }

        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        // Try workspace root then ApiGeneratorManager/ sub-folder
        Path runtimePom = cwd.resolve("api-generator-runtime/pom.xml");
        if (!Files.exists(runtimePom)) {
            runtimePom = cwd.resolve("ApiGeneratorManager/api-generator-runtime/pom.xml");
        }

        if (!Files.exists(runtimePom)) {
            log(jobId, "api-generator-runtime not found in workspace; generated build expects published Maven artifacts.");
            return;
        }

        synchronized (runtimeInstalled) {
            if (runtimeInstalled.get()) {
                return;
            }
            Path mavenRoot = runtimePom.getParent().getParent(); // ApiGeneratorManager/
            String mvnCmd = resolveBuildCommand(mavenRoot, jobId);
            log(jobId, "Installing api-generator-core + runtime into local Maven repo...");
            execAndStream(jobId, mavenRoot, List.of(mvnCmd, "-q",
                    "-pl", "api-generator-core,api-generator-runtime",
                    "-am", "install", "-DskipTests"));
            runtimeInstalled.set(true);
            log(jobId, "Runtime installed.");
        }
    }

    /**
     * Replaces localhost/127.0.0.1 in a JDBC URL with host.docker.internal so
     * the generated container can reach a database running on the host machine.
     */
    private static String toDockerJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) return "";
        return jdbcUrl
                .replace("localhost", "host.docker.internal")
                .replace("127.0.0.1", "host.docker.internal");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String bcrypt(String raw) {
        String value = nullToEmpty(raw);
        if (value.startsWith("$2")) {
            return value;
        }
        return GENERATED_PASSWORD_ENCODER.encode(value);
    }

    private static String dotenv(String raw) {
        String value = nullToEmpty(raw);
        if (value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("Environment values must not contain line breaks.");
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void validateGeneratedSecurity(GeneratorProperties.Security security) {
        if (security == null || !security.isEnabled()) {
            return;
        }
        String bootstrapUsername = nullToEmpty(security.getBootstrapUsername());
        if (!bootstrapUsername.matches("^[A-Za-z0-9._@-]{1,128}$")) {
            throw new IllegalStateException("Generated API bootstrap username contains invalid characters.");
        }
        String bootstrapPassword = nullToEmpty(security.getBootstrapPassword());
        if (isPlaceholderSecret(bootstrapPassword)) {
            throw new IllegalStateException("Generated API bootstrap password must be replaced before generation.");
        }
        if (!bootstrapPassword.startsWith("$2") && bootstrapPassword.length() < 12) {
            throw new IllegalStateException("Generated API bootstrap password must be at least 12 characters or a BCrypt hash.");
        }
        String jwtSecret = nullToEmpty(security.getJwtSecret());
        if (isPlaceholderSecret(jwtSecret)) {
            throw new IllegalStateException("Generated API JWT secret must be replaced before generation.");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("Generated API JWT secret must be at least 32 characters.");
        }
        if (containsUnsafePlaceholderDefault(jwtSecret)) {
            throw new IllegalStateException("Generated API JWT secret contains invalid characters.");
        }
        String jwtIssuer = nullToEmpty(security.getJwtIssuer());
        if (jwtIssuer.isBlank()) {
            throw new IllegalStateException("Generated API JWT issuer is required.");
        }
        if (!jwtIssuer.matches("^[A-Za-z0-9._:/@-]{1,200}$")) {
            throw new IllegalStateException("Generated API JWT issuer contains invalid characters.");
        }
        if (security.getJwtExpirationSeconds() < 60 || security.getJwtExpirationSeconds() > 2_592_000) {
            throw new IllegalStateException("Generated API JWT expiration must be between 60 seconds and 30 days.");
        }
    }

    private static boolean isPlaceholderSecret(String value) {
        String normalized = nullToEmpty(value).toLowerCase(Locale.ROOT);
        return normalized.contains("replace_with")
                || normalized.contains("change_me")
                || normalized.contains("dev-only")
                || normalized.contains("changeme");
    }

    private static boolean containsUnsafePlaceholderDefault(String value) {
        String normalized = nullToEmpty(value);
        return normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0
                || normalized.indexOf('\0') >= 0
                || normalized.contains("${")
                || normalized.contains("}");
    }

    int reserveDockerHostPort(Integer preferredPort) {
        synchronized (reservedDockerHostPorts) {
            int hostPort = resolveDockerHostPort(
                    preferredPort,
                    jobProperties.getDockerBindHost(),
                    jobProperties.getDockerFallbackPort(),
                    reservedDockerHostPorts
            );
            reservedDockerHostPorts.add(hostPort);
            return hostPort;
        }
    }

    static int resolveDockerHostPort(Integer preferredPort,
                                     String bindHost,
                                     int fallbackPort,
                                     Set<Integer> reservedPorts) {
        Set<Integer> safeReservedPorts = reservedPorts == null ? Set.of() : reservedPorts;
        if (preferredPort != null && isHostPortAvailable(bindHost, preferredPort, safeReservedPorts)) {
            return preferredPort;
        }

        int firstCandidate = preferredPort == null ? fallbackPort : preferredPort + 1;
        int nextFreePort = findFreePort(bindHost, firstCandidate, safeReservedPorts);
        if (nextFreePort > 0) {
            return nextFreePort;
        }

        if (isHostPortAvailable(bindHost, fallbackPort, safeReservedPorts)) {
            return fallbackPort;
        }
        nextFreePort = findFreePort(bindHost, fallbackPort + 1, safeReservedPorts);
        if (nextFreePort > 0) {
            return nextFreePort;
        }

        throw new IllegalStateException("No available Docker host port could be found.");
    }

    private void releaseDockerHostPort(Integer hostPort) {
        if (hostPort != null) {
            reservedDockerHostPorts.remove(hostPort);
        }
    }

    private static int findFreePort(String bindHost, int firstCandidate, Set<Integer> reservedPorts) {
        if (firstCandidate <= 0) {
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(resolveBindAddress(bindHost), 0));
                return socket.getLocalPort();
            } catch (IOException e) {
                return -1;
            }
        }

        for (int candidate = Math.max(1, firstCandidate); candidate <= 65535; candidate++) {
            if (isHostPortAvailable(bindHost, candidate, reservedPorts)) {
                return candidate;
            }
        }
        return -1;
    }

    private static boolean isHostPortAvailable(String bindHost, int port, Set<Integer> reservedPorts) {
        if (port < 1 || port > 65535 || reservedPorts.contains(port)) {
            return false;
        }
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(resolveBindAddress(bindHost), port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean usesPersistedWorker() {
        return jobRecordRepository != null
                && (jobProperties.isWorkerEnabled() || isRequestOnlyBackend());
    }

    private boolean isRequestOnlyBackend() {
        return jobProperties.isDockerRequestEnabled() && !jobProperties.isDockerDeploymentEnabled();
    }

    private static InetAddress resolveBindAddress(String bindHost) {
        String normalized = bindHost == null || bindHost.isBlank() ? "127.0.0.1" : bindHost.trim();
        try {
            return InetAddress.getByName(normalized);
        } catch (IOException e) {
            throw new IllegalStateException("Docker bind host could not be resolved: " + normalized, e);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private Path createJobDir(String jobId) {
        try {
            return Files.createTempDirectory(jobProperties.getTempDirectoryPrefix() + jobId + "-");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create temp directory for generation job", e);
        }
    }

    /**
     * Copies the api-generator-template module content into the output directory.
     * Résolution du chemin (dans l'ordre) :
     *  1. Chemin configuré tel quel (absolu ou relatif au working dir JVM)
     *  2. Relatif au dossier parent du working dir (cas IntelliJ avec working dir = sous-module)
     *  3. Relatif au dossier courant + "ApiGeneratorManager/api-generator-template"
     */
    private void copyTemplate(Path outputDir) throws IOException {
        String configured = jobProperties.getTemplatePath();

        Path src = resolve(configured);
        if (src == null) {
            throw new IllegalStateException(
                "Template not found. Configured path: '" + configured + "'. " +
                "Working dir: " + Paths.get("").toAbsolutePath() + ". " +
                "Set APP_TEMPLATE_PATH to the absolute path of api-generator-template.");
        }

        FileUtils.copyDirectory(src, outputDir);
        FileUtils.deleteDirectoryIfExists(outputDir.resolve("target"));
    }

    /** Tente de résoudre un chemin de template — retourne null si aucune candidate n'existe. */
    private static Path resolve(String configured) {
        Path cwd = Paths.get("").toAbsolutePath();

        // 1. Chemin tel que configuré
        Path p = cwd.resolve(configured).normalize();
        if (Files.exists(p)) return p;

        // 2. Relatif au dossier parent du cwd (ex : IntelliJ lance depuis ApiGeneratorManager/)
        Path parent = cwd.getParent();
        if (parent != null) {
            Path p2 = parent.resolve(configured).normalize();
            if (Files.exists(p2)) return p2;
        }

        // 3. Fallback : ./api-generator-template depuis le cwd
        Path p3 = cwd.resolve("api-generator-template").normalize();
        if (Files.exists(p3)) return p3;

        // 4. Fallback : ./ApiGeneratorManager/api-generator-template
        Path p4 = cwd.resolve("ApiGeneratorManager/api-generator-template").normalize();
        if (Files.exists(p4)) return p4;

        return null;
    }

    /**
     * Writes application.yml into the copied template using the user's values.
     * Properties map 1-to-1 to the placeholders already in the template's application.yml.
     * No string building — we use a structured Properties/YAML object.
     */
    private void writeApplicationYml(Path outputDir, GeneratorProperties props) throws IOException {
        var db = props.getDb();
        var sec = props.getSecurity();
        validateGeneratedSecurity(sec);

        // Build the YAML as a structured Java Map — no string concatenation
        var yaml = new java.util.LinkedHashMap<String, Object>();

        var spring = new java.util.LinkedHashMap<String, Object>();
        spring.put("application", Map.of("name", props.getAppName()));
        var ds = new java.util.LinkedHashMap<String, Object>();
        ds.put("url", "${DB_URL:}");
        ds.put("username", "${DB_USERNAME:}");
        ds.put("password", "${DB_PASSWORD:}");
        spring.put("datasource", ds);
        spring.put("jpa", Map.of("open-in-view", false,
                                 "hibernate", Map.of("ddl-auto", "validate")));
        yaml.put("spring", spring);

        yaml.put("server", Map.of(
                "port", "${API_PORT:${PORT:8080}}",
                "forward-headers-strategy", "framework"
        ));
        yaml.put("springdoc", Map.of(
                "swagger-ui", Map.of("disable-swagger-default-url", true)
        ));

        var generator = new java.util.LinkedHashMap<String, Object>();
        generator.put("runtime", Map.of("enabled", true));
        var genDb = new java.util.LinkedHashMap<String, Object>();
        genDb.put("type", "${DB_TYPE:" + (db.getType() != null ? db.getType().name() : "POSTGRESQL") + "}");
        genDb.put("url", "${DB_URL:}");
        genDb.put("username", "${DB_USERNAME:}");
        genDb.put("password", "${DB_PASSWORD:}");
        genDb.put("schema", "${DB_SCHEMA:" + (db.getSchema() != null ? db.getSchema() : "public") + "}");
        generator.put("db", genDb);

        // Table hints — written as-is from the user YAML, no transformation
        if (props.getTables() != null && !props.getTables().isEmpty()) {
            var tablesMap = new java.util.LinkedHashMap<String, Object>();
            props.getTables().forEach((table, hint) -> {
                var h = new java.util.LinkedHashMap<String, Object>();
                if (hint.getSoftDeleteColumn()   != null) h.put("softDeleteColumn",     hint.getSoftDeleteColumn());
                if (hint.getCreatedByColumn()     != null) h.put("createdByColumn",      hint.getCreatedByColumn());
                if (hint.getLastModifiedByColumn()!= null) h.put("lastModifiedByColumn", hint.getLastModifiedByColumn());
                if (!hint.getJsonColumns().isEmpty())      h.put("jsonColumns",          hint.getJsonColumns());
                if (!hint.getArrayColumns().isEmpty())     h.put("arrayColumns",         hint.getArrayColumns());
                tablesMap.put(table, h);
            });
            generator.put("tables", tablesMap);
        }
        yaml.put("generator", generator);

        var security = new java.util.LinkedHashMap<String, Object>();
        security.put("enabled", "${SECURITY_ENABLED:" + sec.isEnabled() + "}");
        security.put("bootstrap", Map.of(
            "username", "${BOOTSTRAP_USER:}",
            "password", "${BOOTSTRAP_PASSWORD_HASH:}"));
        security.put("jwt", Map.of(
            "secret",             "${JWT_SECRET}",
            "issuer",             "${JWT_ISSUER:" + (sec.getJwtIssuer() != null ? sec.getJwtIssuer() : props.getAppName()) + "}",
            "expiration-seconds", "${JWT_EXP_SECONDS:" + sec.getJwtExpirationSeconds() + "}"));
        yaml.put("security", security);

        // Serialize via Jackson YAML — structured, no string building
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        Path target = outputDir.resolve("src/main/resources/application.yml");
        Files.createDirectories(target.getParent());
        mapper.writeValue(target.toFile(), yaml);
    }

    private void writeSchemaJson(Path outputDir, GeneratorProperties props) throws Exception {
        List<TableInfo> tables;
        if (props.getSchemaTables() != null && !props.getSchemaTables().isEmpty()) {
            tables = props.getSchemaTables();
        } else {
            var db = props.getDb();
            var request = new SchemaReadRequest(
                    db.getType(),
                    db.getUrl(),
                    db.getUsername(),
                    db.getPassword(),
                    db.getSchema(),
                    db.getProperties(),
                    toSchemaReadHints(props.getTables())
            );
            tables = schemaReader.readSchema(request);
        }
        if (tables.isEmpty()) {
            throw new IllegalStateException("No tables were found in the configured database/schema. Prepare your database schema before generating the API.");
        }
        Path target = outputDir.resolve("src/main/resources/schema.json");
        Files.createDirectories(target.getParent());
        new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(target.toFile(), tables);
    }

    private void writeGeneratedApplicationSources(Path outputDir, GeneratorProperties props) throws IOException {
        String basePackage = requireSafeBasePackage(props.getBasePackage());
        Path mainJavaRoot = outputDir.resolve("src/main/java");
        Path testJavaRoot = outputDir.resolve("src/test/java");
        Path templateApplication = mainJavaRoot.resolve("com/example/api/GeneratedApiApplication.java");
        Files.deleteIfExists(templateApplication);
        deleteEmptyParents(templateApplication.getParent(), mainJavaRoot);

        Path applicationTarget = mainJavaRoot
                .resolve(basePackage.replace('.', '/'))
                .resolve("GeneratedApiApplication.java");
        Files.createDirectories(applicationTarget.getParent());
        Files.writeString(applicationTarget, generatedApplicationSource(basePackage),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Path testTarget = testJavaRoot
                .resolve(basePackage.replace('.', '/'))
                .resolve("GeneratedApiApplicationTests.java");
        Files.createDirectories(testTarget.getParent());
        Files.writeString(testTarget, generatedApplicationTestSource(basePackage),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeGeneratedProjectSupportFiles(Path outputDir, GeneratorProperties props) throws IOException {
        String appName = safeDocValue(props.getAppName(), "Generated API");
        String basePackage = requireSafeBasePackage(props.getBasePackage());

        Files.writeString(outputDir.resolve("README.md"), generatedReadme(appName, basePackage),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(outputDir.resolve(".env.example"), generatedEnvExample(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(outputDir.resolve(".gitignore"), generatedGitignore(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Path docs = outputDir.resolve("docs");
        Files.createDirectories(docs);
        Files.writeString(docs.resolve("customization.md"), generatedCustomizationDoc(basePackage),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(docs.resolve("security.md"), generatedSecurityDoc(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(docs.resolve("runtime-model.md"), generatedRuntimeModelDoc(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String requireSafeBasePackage(String basePackage) {
        String value = nullToEmpty(basePackage).trim();
        if (!value.matches("^[a-zA-Z_$][a-zA-Z\\d_$]*(\\.[a-zA-Z_$][a-zA-Z\\d_$]*)*$")) {
            throw new IllegalStateException("Generated API base package contains invalid characters.");
        }
        return value;
    }

    private static String safeDocValue(String value, String fallback) {
        String normalized = nullToEmpty(value).replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private static void deleteEmptyParents(Path directory, Path stopAt) throws IOException {
        Path current = directory;
        Path normalizedStop = stopAt.toAbsolutePath().normalize();
        while (current != null && !current.toAbsolutePath().normalize().equals(normalizedStop)) {
            try (var children = Files.list(current)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    private static String generatedApplicationSource(String basePackage) {
        return """
                package %s;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class GeneratedApiApplication {

                    public static void main(String[] args) {
                        SpringApplication.run(GeneratedApiApplication.class, args);
                    }
                }
                """.formatted(basePackage);
    }

    private static String generatedApplicationTestSource(String basePackage) {
        return """
                package %s;

                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;

                import static org.junit.jupiter.api.Assertions.assertNotNull;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                @SpringBootTest(properties = {
                        "generator.runtime.enabled=false",
                        "spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
                })
                class GeneratedApiApplicationTests {

                    @Test
                    void contextLoads() {
                    }

                    @Test
                    void schemaJsonIsPackaged() throws Exception {
                        try (var schema = Thread.currentThread().getContextClassLoader().getResourceAsStream("schema.json")) {
                            assertNotNull(schema, "schema.json must be available on the classpath");
                            assertTrue(schema.readAllBytes().length > 0, "schema.json must not be empty");
                        }
                    }
                }
                """.formatted(basePackage);
    }

    private static String generatedReadme(String appName, String basePackage) {
        return """
                # %s

                ## Overview

                This project is a generated Spring Boot API base.
                It includes the ApiGeneratorManager runtime dependency and a schema description used to expose API behavior.
                You can extend the project like a standard Spring Boot application by adding your own controllers, services, configuration classes and integrations.
                ApiGeneratorManager provides the technical starting point; your team remains responsible for business rules, tests, security review and deployment.

                This generated project uses the ApiGeneratorManager runtime dependency. Make sure the dependency is installed in your local Maven repository or provided by your organization.

                ## Prerequisites

                - Java 17+
                - Maven 3.9+
                - Docker, optional
                - Access to a relational database compatible with your configuration

                ## Build

                ```bash
                mvn clean package
                ```

                ## Run Locally

                Configure the required environment variables before starting the API. Use `.env.example` as a reference, but do not commit real secrets.

                ```bash
                mvn spring-boot:run
                ```

                ## Environment Variables

                - `DB_URL`
                - `DB_USERNAME`
                - `DB_PASSWORD`
                - `DB_TYPE`
                - `DB_SCHEMA`
                - `SECURITY_ENABLED`
                - `BOOTSTRAP_USER`
                - `BOOTSTRAP_PASSWORD_HASH`
                - `JWT_SECRET`
                - `JWT_ISSUER`
                - `JWT_EXP_SECONDS`

                Do not commit real secrets.
                Use environment variables or your secret manager in production.

                ## Docker

                ```bash
                docker compose up --build
                ```

                The compose file reads values from your environment or from a local `.env` file. Keep `.env` private.

                ## API Documentation

                If Swagger/OpenAPI is enabled, open the Swagger UI exposed by the running Spring Boot application, usually at `/swagger-ui/index.html`.

                ## Customization

                You can add your own Spring Boot code under your application package, for example:

                - custom controllers
                - services
                - configuration classes
                - integrations
                - business validations
                - scheduled jobs

                Recommended package: `%s.custom`.

                See `docs/customization.md` for examples.

                ## Security Notes

                - Replace `JWT_SECRET` with a strong secret.
                - Do not commit `.env`.
                - Review CORS and security settings.
                - Review generated behavior before production.
                - Add tests.
                - Run a security review before sensitive use.

                See `docs/security.md` for more detail.

                ## What This Generated Project Does

                - Provides a Spring Boot API base.
                - Uses `schema.json` as the generated schema description.
                - Can connect to a configured relational database.
                - Can be extended with standard Spring Boot code.

                ## What This Generated Project Does Not Do

                - Does not replace business logic.
                - Does not guarantee production security.
                - Does not remove the need for tests.
                - Does not make infrastructure decisions for you.
                - Does not expose ApiGeneratorManager internal implementation details.
                """.formatted(appName, basePackage);
    }

    private static String generatedEnvExample() {
        return """
                API_BIND_HOST=127.0.0.1
                API_PORT=8080

                DB_TYPE=POSTGRESQL
                DB_SCHEMA=public
                DB_URL=jdbc:postgresql://localhost:5432/example_db
                DB_USERNAME=example_user
                DB_PASSWORD=change-me

                SECURITY_ENABLED=true
                BOOTSTRAP_USER=admin@example.com
                BOOTSTRAP_PASSWORD_HASH=replace-with-bcrypt-hash

                JWT_SECRET=replace-with-a-strong-secret
                JWT_ISSUER=generated-api
                JWT_EXP_SECONDS=3600
                """;
    }

    private static String generatedGitignore() {
        return """
                target/
                .env
                *.log
                .idea/
                .vscode/
                .DS_Store
                *.iml
                node_modules/
                .tmp/
                temp/
                """;
    }

    private static String generatedCustomizationDoc(String basePackage) {
        return """
                # Customization

                This generated project can be extended like a standard Spring Boot application.
                Add your own code under your application package so it remains separate from generated resources.

                Recommended package:

                ```text
                %s.custom
                ```

                Typical custom code includes:

                - custom controllers
                - services
                - external integrations
                - business validations
                - scheduled jobs

                Example:

                ```java
                package %s.custom;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class CustomStatusController {

                    @GetMapping("/api/custom/status")
                    public String status() {
                        return "ok";
                    }
                }
                ```

                Keep business rules, external credentials and environment-specific settings outside generated files.
                """.formatted(basePackage, basePackage);
    }

    private static String generatedSecurityDoc() {
        return """
                # Security

                This generated project is a starting point. Review and adapt it before production use.

                - Do not commit `.env`.
                - Use a secret manager in production.
                - Replace `JWT_SECRET` with a strong secret.
                - Provide `BOOTSTRAP_PASSWORD_HASH` explicitly.
                - Limit CORS according to each environment.
                - Verify database permissions and use the least privilege needed by the API.
                - Review `schema.json` before exposing the API.
                - Add tests and code review for custom behavior.
                - Run a security audit before sensitive use.
                - Do not consider the generated project production-ready without validation.
                """;
    }

    private static String generatedRuntimeModelDoc() {
        return """
                # Runtime Model

                This generated project uses a runtime-driven model.
                The generated `schema.json` describes the resources and structure used by the API base.
                Developers can extend the project using standard Spring Boot components.
                The internal implementation of the runtime is intentionally not documented here; this document only explains how to use and extend the generated project safely.
                """;
    }

    private static Map<String, SchemaReadRequest.TableHint> toSchemaReadHints(
            Map<String, GeneratorProperties.TableHint> hints
    ) {
        if (hints == null || hints.isEmpty()) {
            return Map.of();
        }

        var converted = new java.util.LinkedHashMap<String, SchemaReadRequest.TableHint>();
        hints.forEach((table, hint) -> converted.put(
                table.toLowerCase(Locale.ROOT),
                new SchemaReadRequest.TableHint(
                        hint.getSoftDeleteColumn(),
                        hint.getCreatedByColumn(),
                        hint.getLastModifiedByColumn(),
                        hint.getJsonColumns(),
                        hint.getArrayColumns()
                )
        ));
        return converted;
    }

    private void writeDockerDeploymentFiles(Path outputDir) throws IOException {
        Files.writeString(outputDir.resolve("Dockerfile"), """
                FROM eclipse-temurin:17-jre-jammy
                WORKDIR /app
                RUN groupadd --system app && useradd --system --gid app --home-dir /app app
                ARG JAR_FILE=target/*.jar
                COPY ${JAR_FILE} app.jar
                USER app
                EXPOSE 8080
                ENTRYPOINT ["java", "-jar", "/app/app.jar"]
                """, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Files.writeString(outputDir.resolve("docker-compose.yml"), """
                services:
                  api:
                    build:
                      context: .
                      dockerfile: Dockerfile
                    environment:
                      PORT: "8080"
                      API_BIND_HOST: ${API_BIND_HOST:-127.0.0.1}
                      DB_URL: ${DB_URL}
                      DB_USERNAME: ${DB_USERNAME}
                      DB_PASSWORD: ${DB_PASSWORD}
                      DB_TYPE: ${DB_TYPE}
                      DB_SCHEMA: ${DB_SCHEMA:-public}
                      SECURITY_ENABLED: ${SECURITY_ENABLED:-true}
                      BOOTSTRAP_USER: ${BOOTSTRAP_USER:-}
                      BOOTSTRAP_PASSWORD_HASH: ${BOOTSTRAP_PASSWORD_HASH:?BOOTSTRAP_PASSWORD_HASH required}
                      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET required}
                      JWT_ISSUER: ${JWT_ISSUER:-generated-api}
                      JWT_EXP_SECONDS: ${JWT_EXP_SECONDS:-3600}
                    ports:
                      - "${API_BIND_HOST:-127.0.0.1}:${API_PORT:-8080}:8080"
                    security_opt:
                      - no-new-privileges:true
                    cap_drop:
                      - ALL
                    extra_hosts:
                      - "host.docker.internal:host-gateway"
                """, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeDockerManagerDeploymentFile(Path outputDir) throws IOException {
        Files.writeString(outputDir.resolve("docker-compose.manager.yml"), """
                services:
                  api:
                    container_name: ${API_CONTAINER_NAME}
                    networks:
                      default:
                      manager:
                        aliases:
                          - ${API_CONTAINER_ALIAS}

                networks:
                  manager:
                    external: true
                    name: ${API_MANAGER_NETWORK}
                """, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        Files.createDirectories(zipFile.getParent());
        FileUtils.zipDirectory(sourceDir, zipFile);
    }

    private void runPersistedJob(GenerationJobRecord record) {
        GeneratorProperties props;
        try {
            props = deserializeProps(record.getRequestPayloadJson());
        } catch (RuntimeException e) {
            record.setStatus(JobStatus.FAILED);
            record.setUpdatedAt(Instant.now());
            record.setErrorMessage(safeMessage(e.getMessage()));
            jobRecordRepository.save(record);
            return;
        }
        if (props == null) {
            record.setStatus(JobStatus.FAILED);
            record.setUpdatedAt(Instant.now());
            record.setErrorMessage("Persisted generation payload is missing.");
            jobRecordRepository.save(record);
            return;
        }

        JobInfo job = toJobInfo(record);
        jobs.put(job.jobId(), job);
        jobLogs.put(job.jobId(), new ArrayDeque<>(splitLogs(record.getLogs(), Integer.MAX_VALUE)));
        jobUserLogs.put(job.jobId(), new ArrayDeque<>(splitLogs(record.getUserLogs(), Integer.MAX_VALUE)));

        CompletableFuture.runAsync(() -> runJob(
                record.getJobId(),
                props,
                job.zipPath(),
                job.outputDir(),
                record.isBuildRequested(),
                record.isDeployDockerRequested(),
                record.getPreferredPort()
        ));
    }

    private void persistJobSnapshot(JobInfo info,
                                    GeneratorProperties props,
                                    Boolean build,
                                    Boolean deployDocker,
                                    Integer preferredPort) {
        if (jobRecordRepository == null || info == null) {
            return;
        }

        GenerationJobRecord record = jobRecordRepository.findById(info.jobId())
                .orElseGet(GenerationJobRecord::new);
        record.setJobId(info.jobId());
        record.setStatus(info.status());
        record.setCreatedAt(info.createdAt());
        record.setUpdatedAt(Instant.now());
        record.setErrorMessage(info.error());
        record.setZipPath(info.zipPath() == null ? null : info.zipPath().toString());
        record.setOutputDir(info.outputDir() == null ? null : info.outputDir().toString());
        record.setHostPort(info.hostPort());
        record.setApiBaseUrl(info.apiBaseUrl());
        record.setContainerId(info.containerId());
        record.setLogs(joinLogs(jobLogs.get(info.jobId())));
        record.setUserLogs(joinLogs(jobUserLogs.get(info.jobId())));
        if (props != null) {
            record.setRequestPayloadJson(serializeProps(props));
        }
        if (build != null) {
            record.setBuildRequested(build);
        }
        if (deployDocker != null) {
            record.setDeployDockerRequested(deployDocker);
        }
        if (preferredPort != null || record.getPreferredPort() == null) {
            record.setPreferredPort(preferredPort);
        }
        jobRecordRepository.save(record);
    }

    private void persistLogBuffers(String jobId) {
        if (jobRecordRepository == null) {
            return;
        }
        jobRecordRepository.findById(jobId).ifPresent(record -> {
            record.setUpdatedAt(Instant.now());
            record.setLogs(joinLogs(jobLogs.get(jobId)));
            record.setUserLogs(joinLogs(jobUserLogs.get(jobId)));
            jobRecordRepository.save(record);
        });
    }

    private List<String> getPersistedLogs(String jobId, int tail, boolean userFriendly) {
        if (jobRecordRepository == null) {
            return List.of();
        }
        return jobRecordRepository.findById(jobId)
                .map(record -> splitLogs(userFriendly ? record.getUserLogs() : record.getLogs(), tail))
                .orElse(List.of());
    }

    private JobInfo toJobInfo(GenerationJobRecord record) {
        return new JobInfo(
                record.getJobId(),
                record.getStatus(),
                record.getCreatedAt(),
                record.getErrorMessage(),
                record.getZipPath() == null || record.getZipPath().isBlank() ? null : Paths.get(record.getZipPath()),
                record.getOutputDir() == null || record.getOutputDir().isBlank() ? null : Paths.get(record.getOutputDir()),
                record.getHostPort(),
                record.getApiBaseUrl(),
                record.getContainerId()
        );
    }

    private String joinLogs(Deque<String> queue) {
        if (queue == null || queue.isEmpty()) {
            return "";
        }
        return String.join("\n", queue);
    }

    private List<String> splitLogs(String value, int tail) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> lines = List.of(value.split("\\R"));
        int requestedTail = tail <= 0 ? jobProperties.getDefaultLogTail() : tail;
        int fromIndex = Math.max(0, lines.size() - requestedTail);
        return lines.subList(fromIndex, lines.size());
    }

    private String serializeProps(GeneratorProperties props) {
        try {
            return payloadCodec.encode(props);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist generation payload", e);
        }
    }

    private GeneratorProperties deserializeProps(String value) {
        try {
            return payloadCodec.decode(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to restore generation payload", e);
        }
    }
}
