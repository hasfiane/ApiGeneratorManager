package com.api.generator.api.persistence;

import com.api.generator.api.service.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "generation_job")
@Getter
@Setter
public class GenerationJobRecord {

    @Id
    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JobStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @Column(name = "zip_path", length = 2048)
    private String zipPath;

    @Column(name = "output_dir", length = 2048)
    private String outputDir;

    @Column(name = "host_port")
    private Integer hostPort;

    @Column(name = "api_base_url", length = 512)
    private String apiBaseUrl;

    @Column(name = "container_id", length = 512)
    private String containerId;

    @Column(name = "logs", columnDefinition = "TEXT")
    private String logs;

    @Column(name = "user_logs", columnDefinition = "TEXT")
    private String userLogs;

    @Column(name = "request_payload_json", columnDefinition = "TEXT")
    private String requestPayloadJson;

    @Column(name = "build_requested", nullable = false)
    private boolean buildRequested;

    @Column(name = "deploy_docker_requested", nullable = false)
    private boolean deployDockerRequested;

    @Column(name = "preferred_port")
    private Integer preferredPort;

}
