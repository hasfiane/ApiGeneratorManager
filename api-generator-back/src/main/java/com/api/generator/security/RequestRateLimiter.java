package com.api.generator.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RequestRateLimiter {

    private static final long WINDOW_SECONDS = 60;
    private final Map<String, AttemptTracker> attempts = new ConcurrentHashMap<>();

    public boolean allow(String scope, String identifier, int maxRequestsPerMinute) {
        if (scope == null || scope.isBlank() || identifier == null || identifier.isBlank()) {
            return false;
        }
        String key = scope + ":" + identifier;
        long now = Instant.now().getEpochSecond();
        AttemptTracker tracker = attempts.computeIfAbsent(key, ignored -> new AttemptTracker(now));
        synchronized (tracker) {
            if (now - tracker.windowStart > WINDOW_SECONDS) {
                tracker.windowStart = now;
                tracker.count.set(0);
            }
            return tracker.count.incrementAndGet() <= maxRequestsPerMinute;
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        long now = Instant.now().getEpochSecond();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().windowStart > WINDOW_SECONDS * 5);
    }

    private static final class AttemptTracker {
        private long windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        private AttemptTracker(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
