package com.api.generator.admin;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiRequestAuditServiceTest {

    @Test
    void recentReturnsNewestEventsFirstAndClampsLimit() {
        ApiRequestAuditService service = new ApiRequestAuditService();
        service.record(event("first"));
        service.record(event("second"));
        service.record(event("third"));

        List<ApiRequestAuditService.ApiRequestAuditEvent> recent = service.recent(2);

        assertEquals(2, recent.size());
        assertEquals("third", recent.get(0).traceId());
        assertEquals("second", recent.get(1).traceId());
    }

    @Test
    void recentTreatsNonPositiveLimitAsOne() {
        ApiRequestAuditService service = new ApiRequestAuditService();
        service.record(event("first"));
        service.record(event("second"));

        List<ApiRequestAuditService.ApiRequestAuditEvent> recent = service.recent(0);

        assertEquals(1, recent.size());
        assertEquals("second", recent.get(0).traceId());
    }

    private static ApiRequestAuditService.ApiRequestAuditEvent event(String traceId) {
        return new ApiRequestAuditService.ApiRequestAuditEvent(
                Instant.parse("2026-05-09T00:00:00Z"),
                traceId,
                "GET",
                "/api/test",
                200,
                12,
                "operator@example.com",
                "127.0.0.1"
        );
    }
}
