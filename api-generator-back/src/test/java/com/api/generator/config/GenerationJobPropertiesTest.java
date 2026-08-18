package com.api.generator.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationJobPropertiesTest {

    @Test
    void previewRuntimeContainerCommandBindsPublishedPortToConfiguredHost() {
        List<String> command = GenerationJobProperties.ContainerRuntime.DOCKER.runContainerCmd(
                "preview-container",
                "preview-image",
                "127.0.0.1",
                18080,
                List.of("PORT=8080")
        );

        assertTrue(command.contains("-p"));
        assertTrue(command.contains("127.0.0.1:18080:8080"));
    }

    @Test
    void dockerComposeUpCanIncludeManagerNetworkOverride() {
        List<String> command = GenerationJobProperties.ContainerRuntime.DOCKER.composeUpCmd(
                "apigen_job",
                List.of("docker-compose.yml", "docker-compose.manager.yml")
        );

        assertEquals(List.of(
                "docker",
                "compose",
                "-f",
                "docker-compose.yml",
                "-f",
                "docker-compose.manager.yml",
                "-p",
                "apigen_job",
                "up",
                "-d",
                "--build"
        ), command);
    }

    @Test
    void hostedApiBaseUrlUsesStableGeneratedRoute() {
        GenerationJobProperties properties = new GenerationJobProperties();

        assertEquals("/generated/apis/job-123", properties.buildHostedApiBaseUrl("job-123"));
    }
}
