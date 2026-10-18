package com.salestracker.agent;

import com.salestracker.auth.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Caps how often one user can ask the AI for something, so a stuck button, a runaway script or a shared login cannot run
 * up the AI bill or use up the shop's quota. A sliding one-minute window per user, kept in memory: each backend
 * instance counts on its own, so with two instances behind the load balancer the real ceiling is up to twice the limit.
 */
@Component
class AgentRateLimiter {
    private static final long WINDOW_MS = 60_000;

    private final int max;
    private final Clock clock;
    private final Map<Long, Deque<Long>> hits = new HashMap<>();

    @Autowired
    AgentRateLimiter(@Value("${app.agent.max-requests-per-minute:20}") int max) {
        this(max, Clock.systemUTC());
    }

    AgentRateLimiter(int max, Clock clock) {
        this.max = max;
        this.clock = clock;
    }

    /** Counts one request for the user, or throws 429 when they already made the maximum within the last minute. */
    synchronized void check(Long userId) {
        long now = clock.millis();
        if (hits.size() > 1000) hits.values().removeIf(q -> expired(q, now)); // users who went away do not pile up
        Deque<Long> recent = hits.computeIfAbsent(userId, id -> new ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.peekFirst() >= WINDOW_MS) recent.pollFirst();
        if (recent.size() >= max) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "You are sending requests to the assistant too fast. Wait a moment and try again");
        }
        recent.addLast(now);
    }

    private static boolean expired(Deque<Long> q, long now) {
        return q.isEmpty() || now - q.peekLast() >= WINDOW_MS;
    }
}
