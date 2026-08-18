package com.api.generator.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_preview", indexes = {
        @Index(name = "idx_api_preview_generated_api", columnList = "generated_api_id", unique = true)
})
@Getter
@Setter
public class ApiPreview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_api_id", nullable = false)
    private GeneratedApi generatedApi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PreviewStatus status;

    @Column(name = "container_id", length = 128)
    private String containerId;

    @Column(name = "image_tag", length = 160)
    private String imageTag;

    @Column(name = "workspace_dir", length = 1024)
    private String workspaceDir;

    @Column(name = "host_port")
    private Integer hostPort;

    @Column(name = "base_url", length = 256)
    private String baseUrl;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_hint", length = 2048)
    private String errorHint;

    @Column(name = "logs", columnDefinition = "TEXT")
    private String logs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;
}
