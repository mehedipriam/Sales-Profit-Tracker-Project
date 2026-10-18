package com.salestracker.agent;

import com.salestracker.auth.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AgentRateLimiterTest {

    /** A clock the test moves by hand. */
    private static final class TestClock extends Clock {
        long now = 1_000_000;

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
    }

    @Test
    void allowsTheMaximumThenBlocksUntilTheWindowMovesOn() {
        TestClock clock = new TestClock();
        AgentRateLimiter limiter = new AgentRateLimiter(3, clock);

        limiter.check(1L);
        clock.now += 10_000;
        limiter.check(1L);
        clock.now += 10_000;
        limiter.check(1L);
        ApiException e = assertThrows(ApiException.class, () -> limiter.check(1L));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());

        clock.now += 39_999; // 59.999s after the first request: still inside its minute
        assertThrows(ApiException.class, () -> limiter.check(1L));
        clock.now += 1;      // the first request is now a full minute old
        limiter.check(1L);
        assertThrows(ApiException.class, () -> limiter.check(1L));
    }

    @Test
    void eachUserHasTheirOwnAllowance() {
        AgentRateLimiter limiter = new AgentRateLimiter(1, new TestClock());
        limiter.check(1L);
        limiter.check(2L);
        assertThrows(ApiException.class, () -> limiter.check(1L));
        assertThrows(ApiException.class, () -> limiter.check(2L));
    }

    @Test
    void ablockedRequestDoesNotExtendTheBlock() {
        TestClock clock = new TestClock();
        AgentRateLimiter limiter = new AgentRateLimiter(1, clock);
        limiter.check(1L);
        for (int i = 0; i < 5; i++) {
            clock.now += 10_000;
            assertThrows(ApiException.class, () -> limiter.check(1L));
        }
        clock.now += 10_000; // 60s after the one allowed request, however many were refused since
        limiter.check(1L);
    }
}
