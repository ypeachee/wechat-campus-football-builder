package com.campusfootball.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWindowRateLimiterTest {
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();
    private final Instant start = Instant.parse("2026-10-28T10:00:00Z");

    @Test
    void limitsWithinTheWindowAndResetsOnTheNextWindow() {
        assertTrue(limiter.tryAcquire("login:ip-a", 2, Duration.ofMinutes(1), start).allowed());
        assertTrue(limiter.tryAcquire("login:ip-a", 2, Duration.ofMinutes(1), start.plusSeconds(1)).allowed());
        FixedWindowRateLimiter.Decision denied = limiter.tryAcquire(
                "login:ip-a", 2, Duration.ofMinutes(1), start.plusSeconds(10));
        assertFalse(denied.allowed());
        assertEquals(50, denied.retryAfterSeconds());
        assertTrue(limiter.tryAcquire("login:ip-a", 2, Duration.ofMinutes(1), start.plusSeconds(60)).allowed());
    }

    @Test
    void keysAndScopesAreIndependent() {
        assertTrue(limiter.tryAcquire("rating:user-a", 1, Duration.ofMinutes(1), start).allowed());
        assertFalse(limiter.tryAcquire("rating:user-a", 1, Duration.ofMinutes(1), start).allowed());
        assertTrue(limiter.tryAcquire("rating:user-b", 1, Duration.ofMinutes(1), start).allowed());
        assertTrue(limiter.tryAcquire("report:user-a", 1, Duration.ofMinutes(1), start).allowed());
    }

    @Test
    void cleanupRemovesExpiredKeys() {
        limiter.tryAcquire("old", 1, Duration.ofMinutes(1), start);
        limiter.cleanup(start.plus(Duration.ofHours(2)), Duration.ofHours(1));
        assertEquals(0, limiter.size());
    }
}