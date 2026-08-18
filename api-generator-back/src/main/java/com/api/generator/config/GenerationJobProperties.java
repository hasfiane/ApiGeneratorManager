package com.api.generator.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.generation.jobs")
public class GenerationJobProperties {

    public enum ContainerRuntime {
        DOCKER, PODMAN;

        public String binary() {
            return name().toLowerCase(Locale.ROOT);
        }

        public List<String> composeUpCmd(String project) {
            return composeUpCmd(project, List.of("docker-compose.yml"));
        }

        public List<String> composeUpCmd(String project, List<String> composeFiles) {
            java.util.ArrayList<String> cmd = new java.util.ArrayList<>();
            if (this == PODMAN) {
                cmd.add("podman-compose");
            } else {
                cmd.add("docker");
                cmd.add("compose");
            }
            for (String composeFile : composeFiles) {
                cmd.add("-f");
                cmd.add(composeFile);
            }
            cmd.add("-p");
            cmd.add(project);
            cmd.add("up");
            cmd.add("-d");
            cmd.add("--build");
            return List.copyOf(cmd);
        }

        public List<String> composeDownCmd(String project) {
            if (this == PODMAN) {
                return List.of("podman-compose", "-p", project, "down", "-v");
            }
            return List.of("docker", "compose", "-p", project, "down", "-v");
        }

        public List<String> stopContainerCmd(String containerId) {
            return List.of(binary(), "stop", containerId);
        }

        public List<String> rmContainerCmd(String containerId) {
            return List.of(binary(), "rm", containerId);
        }

        public List<String> buildImageCmd(String imageTag, String contextDir) {
            return List.of(binary(), "build", "-t", imageTag, contextDir);
        }

        public List<String> runContainerCmd(String containerName,
                                            String imageTag,
                                            String hostAddress,
                                            int hostPort,
                                            List<String> envPairs) {
            java.util.ArrayList<String> cmd = new java.util.ArrayList<>();
            cmd.add(binary());
            cmd.add("run");
            cmd.add("-d");
            cmd.add("--name");
            cmd.add(containerName);
            cmd.add("--add-host");
            cmd.add("host.docker.internal:host-gateway");
            cmd.add("-p");
            cmd.add(hostAddress + ":" + hostPort + ":8080");
            for (String envPair : envPairs) {
                cmd.add("-e");
                cmd.add(envPair);
            }
            cmd.add(imageTag);
            return List.copyOf(cmd);
        }

        public List<String> removeImageCmd(String imageTag) {
            return List.of(binary(), "rmi", "-f", imageTag);
        }

        public List<String> logsCmd(String containerId, int tail) {
            return List.of(binary(), "logs", "--tail", String.valueOf(Math.max(1, tail)), containerId);
        }
    }

    @NotBlank
    private String outputFolderName = "generated-api";

    @NotBlank
    private String zipFileName = "generated-api.zip";

    @NotBlank
    private String composeProjectPrefix = "apigen_";

    @NotBlank
    private String composeContainerIdPrefix = "compose:";

    @NotBlank
    private String dockerBaseUrlHost = "localhost";

    @NotBlank
    private String dockerBindHost = "127.0.0.1";

    @NotBlank
    private String previewBindHost = "127.0.0.1";

    @Min(1)
    private int maxLogLines = 500;

    @Min(1)
    private int defaultLogTail = 200;

    @Min(10)
    private long commandTimeoutSeconds = 600;

    @Min(100)
    private long workerPollDelayMs = 1000;

    private boolean workerEnabled = true;

    @Min(1)
    private long retentionHours = 24;

    @Min(1)
    private long previewRetentionHours = 12;

    @Min(5)
    private long previewStartupTimeoutSeconds = 120;

    @Min(5)
    private long previewHealthTimeoutSeconds = 60;

    @NotNull
    private List<String> previewHealthProbePaths = List.of(
            "/actuator/health",
            "/v3/api-docs",
            "/swagger-ui/index.html",
            "/"
    );

    @Min(1)
    private int dockerFallbackPort = 18080;

    @Min(1)
    private int cloudbeaverPort = 8978;

    @Min(1)
    private int dbPortOffset = 1;

    private boolean dockerDeploymentEnabled = true;

    private boolean dockerRequestEnabled = true;

    private boolean dockerHostedRouteEnabled = false;

    @NotBlank
    private String dockerHostedRoutePrefix = "/generated/apis";

    @NotBlank
    private String dockerManagerNetworkName = "apigen-manager-docker_default";

    @NotBlank
    private String downloadFileNameTemplate = "generated-api-%s.zip";

    @NotBlank
    private String tempDirectoryPrefix = "api-generator-";

    @NotBlank
    private String templatePath = "./api-generator-template";

    @NotNull
    private ContainerRuntime containerRuntime = ContainerRuntime.DOCKER;

    public String buildApiBaseUrl(int hostPort) {
        return "http://" + dockerBaseUrlHost + ":" + hostPort;
    }

    public String buildHostedApiBaseUrl(String jobId) {
        String prefix = dockerHostedRoutePrefix.endsWith("/")
                ? dockerHostedRoutePrefix.substring(0, dockerHostedRoutePrefix.length() - 1)
                : dockerHostedRoutePrefix;
        return prefix + "/" + jobId;
    }

    public String buildDownloadFileName(String jobId) {
        return String.format(Locale.ROOT, downloadFileNameTemplate, jobId);
    }
}
