package com.api.generator.admin;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
public class ApiRequestAuditService {

    private static final int MAX_EVENTS = 500;

    private final Deque<ApiRequestAuditEvent> events = new ArrayDeque<>(MAX_EVENTS);

    public synchronized void record(ApiRequestAuditEvent event) {
        while (events.size() >= MAX_EVENTS) {
            events.removeFirst();
        }
        events.addLast(event);
    }

    public synchronized List<ApiRequestAuditEvent> recent(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<ApiRequestAuditEvent> snapshot = new ArrayList<>(events);
        int fromIndex = Math.max(snapshot.size() - safeLimit, 0);
        List<ApiRequestAuditEvent> result = snapshot.subList(fromIndex, snapshot.size());
        java.util.Collections.reverse(result);
        return List.copyOf(result);
    }

    public record ApiRequestAuditEvent(
            Instant timestamp,
            String traceId,
            String method,
            String path,
            int status,
            long durationMs,
            String principal,
            String clientIp
    ) {
    }
}
