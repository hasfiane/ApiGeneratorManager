package com.api.generator.api.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationJobServiceLoggingTest {

    @Test
    void fallsBackToNextAvailablePortWhenPreferredPortIsBusy() throws IOException {
        try (ServerSocket busyPort = new ServerSocket()) {
            busyPort.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            int selectedPort = GenerationJobService.resolveDockerHostPort(
                    busyPort.getLocalPort(),
                    "127.0.0.1",
                    busyPort.getLocalPort(),
                    Set.of()
            );

            assertTrue(selectedPort > 0);
            assertFalse(selectedPort == busyPort.getLocalPort());
        }
    }

    @Test
    void skipsPortsReservedByAnotherGenerationInThisManager() {
        int selectedPort = GenerationJobService.resolveDockerHostPort(
                18080,
                "127.0.0.1",
                18080,
                Set.of(18080, 18081)
        );

        assertFalse(selectedPort == 18080);
        assertFalse(selectedPort == 18081);
    }

    @Test
    void suppressesNoisyMavenTransferLines() {
        assertTrue(GenerationJobService.shouldSuppressTechnicalLogLine("Progress (1): 16/131 kB"));
        assertTrue(GenerationJobService.shouldSuppressTechnicalLogLine("Downloading from central: https://repo.maven.apache.org/maven2/..."));
        assertTrue(GenerationJobService.shouldSuppressTechnicalLogLine("Downloaded from central: https://repo.maven.apache.org/maven2/..."));
        assertFalse(GenerationJobService.shouldSuppressTechnicalLogLine("[INFO] BUILD SUCCESS"));
    }

    @Test
    void summarizesConnectionFailuresWithActionableMessage() {
        String summary = GenerationJobService.summarizeFailure(new IllegalStateException("The connection attempt failed."));

        assertEquals("Database connection failed. Check the JDBC host, port, database name, credentials, and SSL settings.", summary);
    }

    @Test
    void extractsUsefulMavenBuildFailureDetail() {
        String summary = GenerationJobService.describeCommandFailure(
                List.of("mvn", "clean", "verify"),
                1,
                List.of(
                        "[INFO] --- compiler:3.14.1:compile (default-compile) @ api-generator-template ---",
                        "[ERROR] COMPILATION ERROR :",
                        "[ERROR] No compiler is provided in this environment. Perhaps you are running on a JRE rather than a JDK?",
                        "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.14.1:compile (default-compile) on project api-generator-template: Compilation failure"
                )
        );

        assertEquals("Build failed: No compiler is provided in this environment. Perhaps you are running on a JRE rather than a JDK?", summary);
    }

    @Test
    void summarizesPortConflictWithChosenPort() {
        String summary = GenerationJobService.summarizeFailure(
                new IllegalStateException("Bind for 0.0.0.0:18080 failed: port is already allocated")
        );

        assertEquals("Docker host port 18080 is already in use. Choose another port.", summary);
    }
}
