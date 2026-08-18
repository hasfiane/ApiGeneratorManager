package com.api.generator.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendNginxConfigContractTest {

    @Test
    void localFrontendRoutesApiAndOauthRequestsToBackend() throws IOException {
        String config = Files.readString(Path.of("../api-generator-front/nginx.conf"));
        assertTrue(config.contains("location /api {"));
        assertTrue(config.contains("location ~ ^/(oauth2|login/oauth2)"));
        assertTrue(config.contains("proxy_pass         http://backend:8080;"));
    }

    @Test
    void localFrontendSupportsSpaRouteFallback() throws IOException {
        String config = Files.readString(Path.of("../api-generator-front/nginx.conf"));
        assertTrue(config.contains("try_files $uri $uri/ /index.html;"));
    }
}
