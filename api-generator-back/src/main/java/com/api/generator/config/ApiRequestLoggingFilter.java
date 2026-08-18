package com.api.generator.config;

import com.api.generator.admin.ApiRequestAuditService;
import com.api.generator.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Lightweight request logger to help debug Front <-> Back communication.
 * Logs only API routes to avoid noise.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);
    private static final String ADMIN_DASHBOARD_PATH = "/api/admin/dashboard";
    private static final int MAX_AUDITED_PATH_LENGTH = 240;

    private final ApiRequestAuditService auditService;
    private final ClientIpResolver clientIpResolver;

    public ApiRequestLoggingFilter(ApiRequestAuditService auditService, ClientIpResolver clientIpResolver) {
        this.auditService = auditService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/") || ADMIN_DASHBOARD_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String uri = auditedPath(request.getRequestURI());
        Instant startedAt = Instant.now();

        try {
            filterChain.doFilter(request, response);
        } finally {
            Object traceIdAttribute = request.getAttribute(TraceIdFilter.TRACE_ID_KEY);
            String traceId = traceIdAttribute instanceof String value && !value.isBlank() ? value : null;
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            String principal = currentPrincipal();
            auditService.record(new ApiRequestAuditService.ApiRequestAuditEvent(
                    startedAt,
                    traceId,
                    method,
                    uri,
                    response.getStatus(),
                    durationMs,
                    principal,
                    clientIpResolver.resolve(request)
            ));
            log.debug("traceId={} {} {} -> {} durationMs={}", traceId, method, uri, response.getStatus(), durationMs);
        }
    }

    private static String auditedPath(String uri) {
        if (uri == null || uri.length() <= MAX_AUDITED_PATH_LENGTH) {
            return uri;
        }
        return uri.substring(0, MAX_AUDITED_PATH_LENGTH) + "…";
    }

    private String currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? null : name;
    }
}
