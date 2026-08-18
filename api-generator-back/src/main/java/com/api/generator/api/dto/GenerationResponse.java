package com.api.generator.api.dto;

/**
 * Response returned when a generation job is created.
 */
public class GenerationResponse {
    private String jobId;
    private String statusUrl;
    private String downloadUrl;
    private String generatedApiId;
    private String generationStatus;

    public GenerationResponse() {
    }

    public GenerationResponse(String jobId, String statusUrl, String downloadUrl, String generatedApiId, String generationStatus) {
        this.jobId = jobId;
        this.statusUrl = statusUrl;
        this.downloadUrl = downloadUrl;
        this.generatedApiId = generatedApiId;
        this.generationStatus = generationStatus;
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getStatusUrl() { return statusUrl; }
    public void setStatusUrl(String statusUrl) { this.statusUrl = statusUrl; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getGeneratedApiId() { return generatedApiId; }
    public void setGeneratedApiId(String generatedApiId) { this.generatedApiId = generatedApiId; }

    public String getGenerationStatus() { return generationStatus; }
    public void setGenerationStatus(String generationStatus) { this.generationStatus = generationStatus; }
}
