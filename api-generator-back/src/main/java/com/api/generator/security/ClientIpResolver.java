package com.api.generator.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the real client IP address.
 *
 * By default (APP_TRUST_PROXY=false) only uses remoteAddr, which is safe against
 * X-Forwarded-For spoofing. Set APP_TRUST_PROXY=true only when this application
 * sits behind a reverse proxy that strips / overwrites X-Forwarded-For from
 * untrusted sources (nginx, AWS ALB, Traefik, etc.).
 */
@Component
public class ClientIpResolver {

    private final boolean trustProxy;

    public ClientIpResolver(@Value("${app.security.trust-proxy:false}") boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    public String resolve(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp;
            }
        }
        return request.getRemoteAddr();
    }
}
