package com.api.generator.api.service;

import com.api.generator.account.ApiPreview;
import com.api.generator.account.GeneratedApi;

public interface PreviewRuntimeService {

    StartResult start(GeneratedApi generatedApi, PreviewLaunchConfig config) throws Exception;

    void stop(ApiPreview preview) throws Exception;

    java.util.List<String> logs(ApiPreview preview, int tail) throws Exception;

    HostDiagnostics diagnoseHost();

    record StartResult(String containerId, String imageTag, String workspaceDir, int hostPort, String baseUrl) {
    }

    record HostDiagnostics(String containerRuntime, java.util.List<HostCheck> checks) {
    }

    record HostCheck(String key, boolean ok, String details) {
    }

    record PreviewLaunchConfig(
            String databaseType,
            String jdbcUrl,
            String jdbcUsername,
            String jdbcPassword,
            String schema,
            String bootstrapUsername,
            String bootstrapPassword,
            String jwtSecret,
            String jwtIssuer,
            long jwtExpirationSeconds
    ) {
    }
}
