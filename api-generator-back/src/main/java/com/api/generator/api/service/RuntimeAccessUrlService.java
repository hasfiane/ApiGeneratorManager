package com.api.generator.api.service;

import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class RuntimeAccessUrlService {

    public String resolveDisplayUrl(String internalBaseUrl, String proxyPath) {
        if (proxyPath == null || proxyPath.isBlank()) {
            return internalBaseUrl;
        }
        if (isInternalBaseUrl(internalBaseUrl)) {
            return proxyPath;
        }
        return internalBaseUrl;
    }

    public boolean isInternalBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return true;
            }
            String normalized = host.trim().toLowerCase();
            return normalized.equals("localhost")
                    || normalized.equals("127.0.0.1")
                    || normalized.equals("0.0.0.0")
                    || normalized.equals("::1")
                    || normalized.equals("[::1]");
        } catch (Exception ignored) {
            return true;
        }
    }
}
