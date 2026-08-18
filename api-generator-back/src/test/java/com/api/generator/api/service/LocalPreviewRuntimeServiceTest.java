package com.api.generator.api.service;

import com.api.generator.config.GenerationJobProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalPreviewRuntimeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void buildMavenCommandAppendsArgsWhenResolvedFromMavenHome() throws Exception {
        String previousMavenHome = System.getProperty("maven.home");
        try {
            Path binDir = Files.createDirectories(tempDir.resolve("bin"));
            String mvnBinary = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                    ? "mvn.cmd"
                    : "mvn";
            Path mvnPath = Files.createFile(binDir.resolve(mvnBinary));
            System.setProperty("maven.home", tempDir.toString());

            LocalPreviewRuntimeService service = new LocalPreviewRuntimeService(new GenerationJobProperties());
            Method method = LocalPreviewRuntimeService.class.getDeclaredMethod("buildMavenCommand", Path.class, String[].class);
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<String> command = (List<String>) method.invoke(service, tempDir, new String[]{"--version"});

            assertEquals(List.of(mvnPath.toString(), "--version"), command);
        } finally {
            if (previousMavenHome == null) {
                System.clearProperty("maven.home");
            } else {
                System.setProperty("maven.home", previousMavenHome);
            }
        }
    }
}
