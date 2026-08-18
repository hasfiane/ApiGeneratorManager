package com.api.generator.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter for authentication endpoints to prevent brute-force attacks.
 */
@Component
public class AuthRateLimiter {

    private final int maxAttemptsPerWindow;
    private final long windowSeconds;

    // Track attempts by IP or email
    private final Map<String, AttemptTracker> attempts = new ConcurrentHashMap<>();

    public AuthRateLimiter(
            @Value("${app.security.auth-rate-limit.max-attempts-per-window:10}") int maxAttemptsPerWindow,
            @Value("${app.security.auth-rate-limit.window-seconds:3600}") long windowSeconds
    ) {
        this.maxAttemptsPerWindow = maxAttemptsPerWindow;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Checks if a login attempt is allowed for the given identifier (IP or email).
     *
     * @param identifier The IP address or email to check
     * @return true if rate limit exceeded, false if attempt is allowed
     */
    public boolean isRateLimited(String identifier) {
        if (isDisabled()) {
            return false;
        }
        if (identifier == null || identifier.isBlank()) {
            return true;
        }

        long now = Instant.now().getEpochSecond();
        AttemptTracker tracker = attempts.computeIfAbsent(identifier, k -> new AttemptTracker());

        synchronized (tracker) {
            // Reset if window expired
            if (now - tracker.windowStart > windowSeconds) {
                tracker.windowStart = now;
                tracker.count.set(0);
            }

            return tracker.count.get() >= maxAttemptsPerWindow;
        }
    }

    /**
     * Records a failed attempt for the given identifier.
     */
    public void recordFailure(String identifier) {
        if (isDisabled()) {
            return;
        }
        if (identifier != null && !identifier.isBlank()) {
            long now = Instant.now().getEpochSecond();
            AttemptTracker tracker = attempts.computeIfAbsent(identifier, k -> new AttemptTracker());
            synchronized (tracker) {
                if (now - tracker.windowStart > windowSeconds) {
                    tracker.windowStart = now;
                    tracker.count.set(0);
                }
                tracker.count.incrementAndGet();
            }
        }
    }

    /**
     * Resets the rate limit for a successful login.
     */
    public void reset(String identifier) {
        attempts.remove(identifier);
    }

    /**
     * Gets remaining attempts for an identifier.
     */
    public int getRemainingAttempts(String identifier) {
        if (isDisabled()) {
            return Integer.MAX_VALUE;
        }
        AttemptTracker tracker = attempts.get(identifier);
        if (tracker == null) {
            return maxAttemptsPerWindow;
        }

        long now = Instant.now().getEpochSecond();
        synchronized (tracker) {
            // Check if window expired
            if (now - tracker.windowStart > windowSeconds) {
                return maxAttemptsPerWindow;
            }
            return Math.max(0, maxAttemptsPerWindow - tracker.count.get());
        }
    }

    /**
     * Cleanup old entries. Runs automatically every 30 minutes.
     */
    @Scheduled(fixedDelay = 1_800_000)
    public void cleanup() {
        if (isDisabled()) {
            attempts.clear();
            return;
        }
        long now = Instant.now().getEpochSecond();
        attempts.entrySet().removeIf(entry -> {
            AttemptTracker tracker = entry.getValue();
            synchronized (tracker) {
                return now - tracker.windowStart > windowSeconds * 2; // Keep for 2 windows
            }
        });
    }

    private boolean isDisabled() {
        return maxAttemptsPerWindow <= 0 || windowSeconds <= 0;
    }

    private static class AttemptTracker {
        long windowStart = Instant.now().getEpochSecond();
        AtomicInteger count = new AtomicInteger(0);
    }
}
