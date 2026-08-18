package com.api.generator.api.controller;

import com.api.generator.account.AppUser;
import com.api.generator.account.GeneratedApi;
import com.api.generator.account.repo.AppUserRepository;
import com.api.generator.account.service.AccountService;
import com.api.generator.account.service.ApiProjectService;
import com.api.generator.account.service.PlanCapabilityService;
import com.api.generator.api.dto.GenerateRequest;
import com.api.generator.api.dto.GenerationResponse;
import com.api.generator.api.dto.JobStatusResponse;
import com.api.generator.api.service.PreviewConfigCodec;
import com.api.generator.api.service.GenerationService;
import com.api.generator.api.service.GenerationJobService;
import com.api.generator.api.service.JobInfo;
import com.api.generator.api.service.PreviewRuntimeService;
import com.api.generator.api.service.RuntimeAccessUrlService;
import com.api.generator.api.service.YamlSchemaSourceReader;
import com.api.generator.config.GeneratorProperties;
import com.api.generator.schema.DatabaseType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api")
@Slf4j
public class GenerationController {

    private final GenerationJobService jobService;
    private final GenerationService generationService;
    private final PreviewConfigCodec previewConfigCodec;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final GeneratorProperties baseProps;
    private final YamlSchemaSourceReader yamlSchemaSourceReader;
    private final com.api.generator.security.JdbcUrlValidator jdbcUrlValidator;
    private final com.api.generator.security.InputSanitizer inputSanitizer;
    private final com.api.generator.security.ClientIpResolver clientIpResolver;
    private final com.api.generator.security.RequestRateLimiter requestRateLimiter;

    private final AppUserRepository users;
    private final ApiProjectService apiProjects;
    private final AccountService accountService;
    private final PlanCapabilityService planCapabilityService;
    private final RuntimeAccessUrlService runtimeAccessUrlService;

    public GenerationController(GenerationJobService jobService,
                                GenerationService generationService,
                                PreviewConfigCodec previewConfigCodec,
                                GeneratorProperties baseProps,
                                com.api.generator.security.JdbcUrlValidator jdbcUrlValidator,
                                com.api.generator.security.InputSanitizer inputSanitizer,
                                com.api.generator.security.ClientIpResolver clientIpResolver,
                                com.api.generator.security.RequestRateLimiter requestRateLimiter,
                                AppUserRepository users,
                                ApiProjectService apiProjects,
                                AccountService accountService,
                                PlanCapabilityService planCapabilityService,
                                RuntimeAccessUrlService runtimeAccessUrlService) {
        this(jobService,
                generationService,
                previewConfigCodec,
                baseProps,
                new YamlSchemaSourceReader(),
                jdbcUrlValidator,
                inputSanitizer,
                clientIpResolver,
                requestRateLimiter,
                users,
                apiProjects,
                accountService,
                planCapabilityService,
                runtimeAccessUrlService);
    }

    @Autowired
    public GenerationController(GenerationJobService jobService,
                                GenerationService generationService,
                                PreviewConfigCodec previewConfigCodec,
                                GeneratorProperties baseProps,
                                YamlSchemaSourceReader yamlSchemaSourceReader,
                                com.api.generator.security.JdbcUrlValidator jdbcUrlValidator,
                                com.api.generator.security.InputSanitizer inputSanitizer,
                                com.api.generator.security.ClientIpResolver clientIpResolver,
                                com.api.generator.security.RequestRateLimiter requestRateLimiter,
                                AppUserRepository users,
                                ApiProjectService apiProjects,
                                AccountService accountService,
                                PlanCapabilityService planCapabilityService,
                                RuntimeAccessUrlService runtimeAccessUrlService) {
        this.jobService = jobService;
        this.generationService = generationService;
        this.previewConfigCodec = previewConfigCodec;
        this.baseProps = baseProps;
        this.yamlSchemaSourceReader = yamlSchemaSourceReader;
        this.jdbcUrlValidator = jdbcUrlValidator;
        this.inputSanitizer = inputSanitizer;
        this.clientIpResolver = clientIpResolver;
        this.requestRateLimiter = requestRateLimiter;
        this.users = users;
        this.apiProjects = apiProjects;
        this.accountService = accountService;
        this.planCapabilityService = planCapabilityService;
        this.runtimeAccessUrlService = runtimeAccessUrlService;
    }

    @PostMapping(value = "/generate/schema-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GenerationResponse> generateSchemaFile(@RequestPart("file") org.springframework.web.multipart.MultipartFile file,
                                                                 @RequestParam(defaultValue = "true") boolean async,
                                                                 @RequestParam(defaultValue = "true") boolean build,
                                                                 @RequestParam(defaultValue = "false") boolean deployDocker,
                                                                 @RequestParam(required = false) Integer hostPort,
                                                                 jakarta.servlet.http.HttpServletRequest servletRequest,
                                                                 Authentication auth) {
        enforceGenerationRateLimit(servletRequest);
        if (deployDocker) {
            throw new ResponseStatusException(BAD_REQUEST, "YAML_SCHEMA_DOCKER_DEPLOY_UNAVAILABLE");
        }

        YamlSchemaSourceReader.NormalizedYamlSchema schema = yamlSchemaSourceReader.read(file);
        GeneratorProperties props = shallowCopy(baseProps);
        if (schema.appName() != null && !schema.appName().isBlank()) {
            props.setAppName(schema.appName());
        }
        if (schema.packageName() != null && !schema.packageName().isBlank()) {
            props.setBasePackage(schema.packageName());
        }
        props.getDb().setType(schema.databaseType());
        props.getDb().setUrl(null);
        props.getDb().setUsername(null);
        props.getDb().setPassword(null);
        props.getDb().setSchema(schema.schema());
        props.getDb().setProperties(new LinkedHashMap<>());
        props.setTables(Map.of());
        props.setSchemaTables(schema.tables());

        AppUser u = requireUser(auth);
        planCapabilityService.ensureCanStartGeneration(u, build, false);

        return ResponseEntity.status(ACCEPTED)
                .body(startTrackedGeneration(
                        u,
                        props,
                        buildPreviewConfig(props),
                        () -> jobService.startGeneration(props, async, build, false, hostPort)
                ));
    }

    /**
     * Launches a new API generation from JSON (coming from the dashboard).
     * Uses manager defaults as base and overrides values provided by the UI.
     */
    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenerationResponse> generateJson(@Valid @RequestBody GenerateRequest request,
                                                          @RequestParam(defaultValue = "true") boolean async,
                                                          jakarta.servlet.http.HttpServletRequest servletRequest,
                                                          Authentication auth) {
        enforceGenerationRateLimit(servletRequest);

        GeneratorProperties props = shallowCopy(baseProps);

        // SECURITY: Validate app name to prevent command injection
        if (request.getAppName() != null && !request.getAppName().isBlank()) {
            String normalizedAppName = inputSanitizer.normalizeAppName(request.getAppName());
            inputSanitizer.validateAppName(normalizedAppName);
            props.setAppName(normalizedAppName);
        }

        // SECURITY: Validate package name
        if (request.getBasePackage() != null && !request.getBasePackage().isBlank()) {
            inputSanitizer.validatePackageName(request.getBasePackage());
            props.setBasePackage(request.getBasePackage());
        }

        // Database details MUST be provided by the front-end (validated on DTO)
        props.getDb().setType(parseDbType(request.getDatabaseType()));

        // SECURITY: Validate JDBC URL to prevent SQL injection
        jdbcUrlValidator.validate(request.getJdbcUrl());
        props.getDb().setUrl(request.getJdbcUrl());

        props.getDb().setUsername(validateDbCredential(request.getJdbcUsername(), "jdbcUsername", true));
        props.getDb().setPassword(validateDbCredential(request.getJdbcPassword(), "jdbcPassword", false));
        props.getDb().setProperties(new LinkedHashMap<>());

        // SECURITY: Validate schema name
        validateSchemaName(request.getSchema());
        props.getDb().setSchema((request.getSchema() != null && !request.getSchema().isBlank()) ? request.getSchema() : null);

        // SECURITY: Sanitize URL for logging (removes passwords)
        log.info("Generate request appName={} dbType={} url={}",
            props.getAppName(),
            props.getDb().getType(),
            jdbcUrlValidator.sanitizeForLogging(props.getDb().getUrl()));

        AppUser u = requireUser(auth);
        planCapabilityService.ensureCanStartGeneration(u, request.isBuild(), request.isDeployDocker());

        return ResponseEntity.status(ACCEPTED)
                .body(startTrackedGeneration(
                        u,
                        props,
                        buildPreviewConfig(props),
                        () -> jobService.startGeneration(
                                props,
                                async,
                                request.isBuild(),
                                request.isDeployDocker(),
                                request.getHostPort()
                        )
                ));
    }

    /**
     * Launch generation from an uploaded YAML configuration.
     * Expected multipart form field name: "config"
     */
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GenerationResponse> generateYaml(@RequestPart("config") org.springframework.web.multipart.MultipartFile config,
                                                          @RequestParam(defaultValue = "true") boolean async,
                                                          jakarta.servlet.http.HttpServletRequest servletRequest,
                                                          Authentication auth) {
        try {
            enforceGenerationRateLimit(servletRequest);
            String yaml = new String(config.getBytes(), StandardCharsets.UTF_8);
            GeneratorProperties uploadedProps = yamlMapper.readValue(yaml, GeneratorProperties.class);
            GeneratorProperties props = sanitizeUploadedConfig(uploadedProps);

            AppUser u = requireUser(auth);
            planCapabilityService.ensureCanStartGeneration(u, true, false);

            return ResponseEntity.status(ACCEPTED)
                    .body(startTrackedGeneration(u, props, buildPreviewConfig(props), () -> jobService.startGeneration(props, async)));
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid config: " + e.getMessage(), e);
        }
    }

    @GetMapping(value = "/generate/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JobStatusResponse> status(@PathVariable String jobId, Authentication auth) {
        inputSanitizer.validateJobId(jobId);
        apiProjects.requireOwner(jobId, auth.getName());
        AppUser user = requireUser(auth);

        JobInfo job = jobService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Unknown jobId"));
        GeneratedApi generatedApi = generationService.findOwnedByJobIdOrNull(jobId, auth.getName());
        String proxyUrl = generatedApi == null ? null : "/api/account/apis/" + generatedApi.getId() + "/proxy";
        // If deployed, mirror URL into DB record (best-effort)
        if (job.apiBaseUrl() != null && !job.apiBaseUrl().isBlank()) {
            apiProjects.markDeployedByJobIdAndReturnFirstUncharged(jobId, auth.getName(), job.apiBaseUrl(), null)
                    .ifPresent(ignored -> accountService.incrementMonthlyDockerDeployment(user.getId(), planCapabilityService));
        }

        return ResponseEntity.ok(new JobStatusResponse(
                job.jobId(),
                job.status(),
                job.createdAt(),
                job.error(),
                job.hostPort(),
                job.apiBaseUrl(),
                proxyUrl,
                job.containerId()
        ));
    }

    private static final int MAX_LOG_TAIL = 500;

    @GetMapping(value = "/generate/{jobId}/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> logs(@PathVariable String jobId,
                                             @RequestParam(defaultValue = "200") int tail,
                                             @RequestParam(defaultValue = "user") String audience,
                                             Authentication auth) {
        inputSanitizer.validateJobId(jobId);
        apiProjects.requireOwner(jobId, auth.getName());
        jobService.getJob(jobId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Unknown jobId"));
        boolean userFriendly = !"dev".equalsIgnoreCase(audience);
        return ResponseEntity.ok(jobService.getLogs(jobId, Math.min(tail, MAX_LOG_TAIL), userFriendly));
    }

    @PostMapping(value = "/generate/{jobId}/stop")
    public ResponseEntity<Void> stop(@PathVariable String jobId, Authentication auth) {
        inputSanitizer.validateJobId(jobId);
        apiProjects.requireOwner(jobId, auth.getName());
        boolean ok = jobService.stopJob(jobId);
        if (!ok) throw new ResponseStatusException(NOT_FOUND, "Unknown jobId");
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/generate/{jobId}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable String jobId, Authentication auth) {
        inputSanitizer.validateJobId(jobId);
        AppUser user = requireUser(auth);
        apiProjects.requireOwner(jobId, auth.getName());
        planCapabilityService.ensureCanDownloadZip(user);
        Path zip = jobService.getZipIfReady(jobId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Job not finished or no zip available"));
        boolean firstDownload = apiProjects.markZipDownloadedIfFirst(jobId, auth.getName());
        if (!firstDownload) {
            throw new ResponseStatusException(CONFLICT, "ZIP already downloaded for this generation");
        }
        generationService.markZipDownloadedIfFirst(jobId, auth.getName());
        accountService.incrementMonthlyZipDownload(user.getId(), planCapabilityService);

        FileSystemResource res = new FileSystemResource(zip.toFile());
        String fileName = jobService.getDownloadFileName(jobId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(res);
    }

    @DeleteMapping(value = "/generate/{jobId}")
    public ResponseEntity<Void> delete(@PathVariable String jobId, Authentication auth) {
        inputSanitizer.validateJobId(jobId);
        apiProjects.requireOwner(jobId, auth.getName());
        boolean deleted = jobService.deleteJob(jobId);
        if (!deleted) throw new ResponseStatusException(NOT_FOUND, "Unknown jobId");
        return ResponseEntity.noContent().build();
    }

    private AppUser requireUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
    }

    private void enforceGenerationRateLimit(jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (!requestRateLimiter.allow("generation", clientIp, 10)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many generation requests. Please try again later.");
        }
    }

    private GenerationResponse startTrackedGeneration(AppUser user,
                                                      GeneratorProperties props,
                                                      String previewConfigJson,
                                                      Supplier<String> starter) {
        GeneratedApi generatedApi = generationService.createPending(
                props.getAppName(),
                props.getDb().getType() == null ? null : props.getDb().getType().name(),
                user
        );
        generationService.updatePreviewConfig(generatedApi, previewConfigJson);

        String jobId;
        try {
            jobId = starter.get();
        } catch (RuntimeException e) {
            generationService.markFailed(generatedApi.getId(), e.getMessage());
            throw e;
        }

        generationService.attachJob(generatedApi, jobId);
        accountService.incrementMonthlyGeneration(user, planCapabilityService);
        apiProjects.create(user, props.getAppName(), jobId, String.valueOf(props.getDb().getType()), props.getDb().getUrl());
        generationService.watchJob(generatedApi.getId(), jobId);

        return new GenerationResponse(
                jobId,
                "/api/generate/" + jobId,
                "/api/generate/" + jobId + "/download",
                generatedApi.getId().toString(),
                generatedApi.getStatus().name()
        );
    }

    private GeneratorProperties sanitizeUploadedConfig(GeneratorProperties uploadedProps) {
        if (uploadedProps == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid config: missing generator properties");
        }
        if (uploadedProps.getDb() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid config: missing database properties");
        }

        GeneratorProperties props = shallowCopy(baseProps);

        String normalizedAppName = inputSanitizer.normalizeAppName(uploadedProps.getAppName());
        inputSanitizer.validateAppName(normalizedAppName);
        props.setAppName(normalizedAppName);

        inputSanitizer.validatePackageName(uploadedProps.getBasePackage());
        props.setBasePackage(uploadedProps.getBasePackage().trim());

        if (uploadedProps.getDb().getType() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "db.type must not be blank");
        }
        props.getDb().setType(uploadedProps.getDb().getType());
        jdbcUrlValidator.validate(uploadedProps.getDb().getUrl());
        props.getDb().setUrl(uploadedProps.getDb().getUrl().trim());
        props.getDb().setUsername(validateDbCredential(uploadedProps.getDb().getUsername(), "db.username", true));
        props.getDb().setPassword(validateDbCredential(uploadedProps.getDb().getPassword(), "db.password", false));
        validateSchemaName(uploadedProps.getDb().getSchema());
        props.getDb().setSchema((uploadedProps.getDb().getSchema() != null && !uploadedProps.getDb().getSchema().isBlank())
                ? uploadedProps.getDb().getSchema().trim()
                : null);

        // SECURITY: User-uploaded driver properties, output paths, Maven coordinates,
        // feature flags, and generated security defaults are intentionally not trusted.
        props.getDb().setProperties(new LinkedHashMap<>());
        props.setTables(validateTableHints(uploadedProps.getTables()));

        return props;
    }

    private String validateDbCredential(String value, String fieldName, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new ResponseStatusException(BAD_REQUEST, fieldName + " must not be blank");
            }
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 256) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is too long");
        }
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\0') >= 0) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " contains invalid characters");
        }
        return trimmed;
    }

    private Map<String, GeneratorProperties.TableHint> validateTableHints(Map<String, GeneratorProperties.TableHint> hints) {
        if (hints == null || hints.isEmpty()) {
            return Map.of();
        }
        if (hints.size() > 100) {
            throw new ResponseStatusException(BAD_REQUEST, "Too many table hints");
        }

        Map<String, GeneratorProperties.TableHint> safeHints = new LinkedHashMap<>();
        hints.forEach((table, hint) -> {
            validateSqlIdentifier(table, "table hint name");
            if (hint != null) {
                validateOptionalSqlIdentifier(hint.getSoftDeleteColumn(), "softDeleteColumn");
                validateOptionalSqlIdentifier(hint.getCreatedByColumn(), "createdByColumn");
                validateOptionalSqlIdentifier(hint.getLastModifiedByColumn(), "lastModifiedByColumn");
                validateIdentifierList(hint.getJsonColumns(), "jsonColumns");
                validateIdentifierList(hint.getArrayColumns(), "arrayColumns");
            }
            safeHints.put(table, hint);
        });
        return safeHints;
    }

    private void validateIdentifierList(List<String> values, String fieldName) {
        if (values == null) {
            return;
        }
        if (values.size() > 100) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " contains too many values");
        }
        values.forEach(value -> validateSqlIdentifier(value, fieldName));
    }

    private void validateOptionalSqlIdentifier(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            validateSqlIdentifier(value, fieldName);
        }
    }

    private void validateSqlIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank() || !value.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,62}$")) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid " + fieldName + ". Only SQL identifiers are allowed.");
        }
    }

    private String buildPreviewConfig(GeneratorProperties props) {
        try {
            PreviewRuntimeService.PreviewLaunchConfig config = new PreviewRuntimeService.PreviewLaunchConfig(
                    props.getDb().getType() == null ? null : props.getDb().getType().name(),
                    props.getDb().getUrl(),
                    props.getDb().getUsername(),
                    props.getDb().getPassword(),
                    props.getDb().getSchema(),
                    props.getSecurity().getBootstrapUsername(),
                    props.getSecurity().getBootstrapPassword(),
                    props.getSecurity().getJwtSecret(),
                    props.getSecurity().getJwtIssuer(),
                    props.getSecurity().getJwtExpirationSeconds()
            );
            return previewConfigCodec.encode(config);
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to persist preview configuration", e);
        }
    }

    private GeneratorProperties shallowCopy(GeneratorProperties src) {
        GeneratorProperties p = new GeneratorProperties();
        p.setAppName(src.getAppName());
        p.setBasePackage(src.getBasePackage());
        p.setOutputDir(src.getOutputDir());
        p.setCleanOutputDir(src.isCleanOutputDir());

        // db
        p.getDb().setType(src.getDb().getType());
        p.getDb().setUrl(src.getDb().getUrl());
        p.getDb().setUsername(src.getDb().getUsername());
        p.getDb().setPassword(src.getDb().getPassword());
        p.getDb().setSchema(src.getDb().getSchema());
        p.getDb().setProperties(new LinkedHashMap<>(src.getDb().getProperties()));
        p.setSchemaTables(src.getSchemaTables() == null ? List.of() : List.copyOf(src.getSchemaTables()));

        // features
        p.getFeatures().setGenerateOpenApi(src.getFeatures().isGenerateOpenApi());
        p.getFeatures().setGenerateDocker(src.getFeatures().isGenerateDocker());
        p.getFeatures().setGenerateClientSdkDocs(src.getFeatures().isGenerateClientSdkDocs());

        // maven
        p.getMaven().setGroupId(src.getMaven().getGroupId());
        p.getMaven().setArtifactId(src.getMaven().getArtifactId());
        p.getMaven().setVersion(src.getMaven().getVersion());

        // security for generated API
        p.getSecurity().setEnabled(src.getSecurity().isEnabled());
        p.getSecurity().setBootstrapUsername(src.getSecurity().getBootstrapUsername());
        p.getSecurity().setBootstrapPassword(src.getSecurity().getBootstrapPassword());
        p.getSecurity().setJwtSecret(src.getSecurity().getJwtSecret());
        p.getSecurity().setJwtIssuer(src.getSecurity().getJwtIssuer());
        p.getSecurity().setJwtExpirationSeconds(src.getSecurity().getJwtExpirationSeconds());

        return p;
    }

    private DatabaseType parseDbType(String raw) {
        String v = raw.trim().toLowerCase();
        if (v.contains("post")) return DatabaseType.POSTGRESQL;
        if (v.contains("mysql")) return DatabaseType.MYSQL;
        if (v.contains("oracle")) return DatabaseType.ORACLE;
        if (v.equals("h2")) return DatabaseType.H2;
        throw new ResponseStatusException(BAD_REQUEST, "Unsupported databaseType: " + raw);
    }

    private void validateSchemaName(String schema) {
        if (schema == null || schema.isBlank()) {
            return; // Schema is optional
        }

        // Allow only alphanumeric, underscore (SQL identifier rules)
        if (!schema.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,62}$")) {
            throw new ResponseStatusException(BAD_REQUEST,
                "Invalid schema name. Only alphanumeric and underscore allowed, max 63 chars.");
        }
    }
}
