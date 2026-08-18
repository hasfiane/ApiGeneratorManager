package com.api.generator.account;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "api_project", indexes = {
        @Index(name = "idx_api_project_owner", columnList = "owner_id")
})
public class ApiProject {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "job_id", length = 64)
    private String jobId;

    @Column(name = "db_type", length = 32)
    private String dbType;

    @Column(name = "source_jdbc_url", length = 512)
    private String sourceJdbcUrlEncoded;

    @Column(name = "created_at", nullable = false)
    private final Instant createdAt = Instant.now();

    @Column(name = "last_deployed_at")
    private Instant lastDeployedAt;

    @Column(name = "api_base_url", length = 256)
    private String apiBaseUrl;

    @Column(name = "docker_image", length = 256)
    private String dockerImage;

    @Column(name = "docker_deployment_quota_charged")
    private Boolean dockerDeploymentQuotaCharged = false;

    @Column(name = "zip_downloaded_at")
    private Instant zipDownloadedAt;

    public boolean isDockerDeploymentQuotaCharged() { return Boolean.TRUE.equals(dockerDeploymentQuotaCharged); }
}
