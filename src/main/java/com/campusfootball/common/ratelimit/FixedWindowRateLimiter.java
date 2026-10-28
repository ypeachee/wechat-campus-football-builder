package com.campusfootball.common.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FixedWindowRateLimiter {
    private static final int CLEANUP_THRESHOLD = 10_000;
    private final ConcurrentHashMap<String, WindowState> windows = new ConcurrentHashMap<>();

    public Decision tryAcquire(String key, int limit, Duration window, Instant now) {
        if (limit < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit and window must be positive");
        }
        long windowMillis = window.toMillis();
        long windowStart = Math.floorDiv(now.toEpochMilli(), windowMillis) * windowMillis;
        long retryAt = windowStart + windowMillis;
        WindowState next = windows.compute(key, (ignored, current) -> {
            if (current == null || current.windowStart != windowStart) {
                return new WindowState(windowStart, 1);
            }
            return new WindowState(windowStart, current.count + 1);
        });
        maybeCleanup(now);
        if (next.count <= limit) {
            return new Decision(true, 0);
        }
        long retryAfterSeconds = Math.max(1, (retryAt - now.toEpochMilli() + 999) / 1000);
        return new Decision(false, retryAfterSeconds);
    }

    public void cleanup(Instant now, Duration retention) {
        long cutoff = now.minus(retention).toEpochMilli();
        windows.entrySet().removeIf(entry -> entry.getValue().windowStart < cutoff);
    }

    public int size() {
        return windows.size();
    }

    private void maybeCleanup(Instant now) {
        if (windows.size() >= CLEANUP_THRESHOLD) {
            cleanup(now, Duration.ofHours(1));
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private record WindowState(long windowStart, int count) {
    }
}