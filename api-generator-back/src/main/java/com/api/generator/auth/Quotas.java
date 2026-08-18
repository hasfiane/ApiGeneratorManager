package com.api.generator.auth;

public record Quotas(
        int monthlyGenerationsUsed,
        int monthlyGenerationsLimit,
        int monthlyDockerDeploymentsUsed,
        int monthlyDockerDeploymentsLimit,
        int monthlyZipDownloadsUsed,
        int monthlyZipDownloadsLimit,
        boolean canBuild,
        boolean canDeployDocker,
        boolean canDownloadZip
) {}
