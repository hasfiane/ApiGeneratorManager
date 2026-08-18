package com.api.generator.api.dto;

import com.api.generator.api.service.JobStatus;

import java.time.Instant;

/**
 * Status payload for a generation job.
 */
public class JobStatusResponse {
    private String jobId;
    private JobStatus status;
    private Instant createdAt;
    private String error;

    // Optional deployment info
    private Integer hostPort;
    private String apiBaseUrl;
    private String proxyUrl;
    private String containerId;

    public JobStatusResponse() {}

    public JobStatusResponse(String jobId, JobStatus status, Instant createdAt, String error,
                             Integer hostPort, String apiBaseUrl, String proxyUrl, String containerId) {
        this.jobId = jobId;
        this.status = status;
        this.createdAt = createdAt;
        this.error = error;
        this.hostPort = hostPort;
        this.apiBaseUrl = apiBaseUrl;
        this.proxyUrl = proxyUrl;
        this.containerId = containerId;
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Integer getHostPort() { return hostPort; }
    public void setHostPort(Integer hostPort) { this.hostPort = hostPort; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getProxyUrl() { return proxyUrl; }
    public void setProxyUrl(String proxyUrl) { this.proxyUrl = proxyUrl; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
}
