package com.api.generator.account.service;

import com.api.generator.account.ApiProject;
import com.api.generator.account.AppUser;
import com.api.generator.account.repo.ApiProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiProjectService {

    private final ApiProjectRepository repo;
    private final EncodingService encoding;

    public ApiProjectService(ApiProjectRepository repo, EncodingService encoding) {
        this.repo = repo;
        this.encoding = encoding;
    }

    public ApiProject create(AppUser owner, String name, String jobId, String dbType, String sourceJdbcUrl) {
        ApiProject p = new ApiProject();
        p.setOwner(owner);
        p.setName(name);
        p.setJobId(jobId);
        p.setDbType(dbType);
        p.setSourceJdbcUrlEncoded(encoding.encode(sourceJdbcUrl));
        return repo.save(p);
    }

    public void markDeployed(ApiProject p, String apiBaseUrl, String dockerImage) {
        p.setApiBaseUrl(apiBaseUrl);
        p.setDockerImage(dockerImage);
        p.setLastDeployedAt(Instant.now());
        repo.save(p);
    }

    @Transactional
    public Optional<ApiProject> markDeployedByJobIdAndReturnFirstUncharged(
            String jobId,
            String ownerEmail,
            String apiBaseUrl,
            String dockerImage
    ) {
        if (jobId == null || ownerEmail == null) return Optional.empty();

        return repo.findByJobIdAndOwner_EmailIgnoreCase(jobId, ownerEmail)
                .flatMap(project -> {
                    markDeployed(project, apiBaseUrl, dockerImage);
                    if (project.isDockerDeploymentQuotaCharged()) {
                        return Optional.empty();
                    }
                    project.setDockerDeploymentQuotaCharged(true);
                    return Optional.of(repo.save(project));
                });
    }

    /**
     * Throws AccessDeniedException if the job does not exist or does not belong to ownerEmail.
     * Deliberately returns the same error for both cases to prevent job-ID enumeration.
     */
    public void requireOwner(String jobId, String ownerEmail) {
        boolean owned = repo.findByJobIdAndOwner_EmailIgnoreCase(jobId, ownerEmail).isPresent();
        if (!owned) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
    }

    public List<ApiProject> listForUser(UUID userId) {
        return repo.findByOwner_IdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public boolean markZipDownloadedIfFirst(String jobId, String ownerEmail) {
        if (jobId == null || ownerEmail == null) {
            return false;
        }
        return repo.findByJobIdAndOwner_EmailIgnoreCase(jobId, ownerEmail)
                .map(project -> {
                    if (project.getZipDownloadedAt() != null) {
                        return false;
                    }
                    project.setZipDownloadedAt(Instant.now());
                    repo.save(project);
                    return true;
                })
                .orElse(false);
    }
}
