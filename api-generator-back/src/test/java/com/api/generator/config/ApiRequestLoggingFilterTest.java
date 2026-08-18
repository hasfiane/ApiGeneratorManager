package com.api.generator.config;

import com.api.generator.admin.ApiRequestAuditService;
import com.api.generator.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApiRequestLoggingFilterTest {

    @Test
    void skipsAdminDashboardPollingToAvoidAuditSelfNoise() throws Exception {
        ApiRequestAuditService auditService = new ApiRequestAuditService();
        ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(auditService, new ClientIpResolver(false));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertTrue(auditService.recent(10).isEmpty());
    }

    @Test
    void recordsApiCallsWithoutQueryStringAndBoundsLongPaths() throws Exception {
        ApiRequestAuditService auditService = new ApiRequestAuditService();
        ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(auditService, new ClientIpResolver(false));
        String longPath = "/api/generate/" + "a".repeat(260);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", longPath);
        request.setQueryString("token=must-not-be-audited");
        request.setRemoteAddr("203.0.113.10");
        request.setAttribute(TraceIdFilter.TRACE_ID_KEY, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> ((MockHttpServletResponse) servletResponse).setStatus(201);

        filter.doFilter(request, response, chain);

        var event = auditService.recent(1).get(0);
        assertEquals("trace-123", event.traceId());
        assertEquals("POST", event.method());
        assertEquals(201, event.status());
        assertEquals("203.0.113.10", event.clientIp());
        assertTrue(event.path().startsWith("/api/generate/"));
        assertTrue(event.path().endsWith("…"));
        assertTrue(event.path().length() <= 241);
    }
}
