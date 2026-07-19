package com.scripty.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/**
 * Caps how many invitations one user may send.
 *
 * <p>Sending an invitation makes the server deliver mail to an address the
 * caller chose, and the view-only path can attach a generated PDF of an entire
 * screenplay. Through a web form that is bounded by how fast a person can type;
 * through an endpoint it is bounded by nothing, which turns one authenticated
 * account into a way to send mail and burn CPU at scale.
 *
 * <p>Deliberately a simple in-memory sliding window rather than anything
 * durable. It is a brake on a single misbehaving session, not a distributed
 * quota — a restart clears it, and a second instance keeps its own count. That
 * is the right trade for a limit whose job is to stop a runaway loop, and it is
 * honest about not being more than that.
 */
@Component
public class InvitationRateLimiter {

    /** Invitations one user may send within the window. */
    private static final int LIMIT = 20;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<Integer, Deque<Instant>> sends = new ConcurrentHashMap<>();

    /**
     * Records an attempt and reports whether it may proceed.
     *
     * @return true when the caller is within their allowance
     */
    public boolean tryAcquire(Integer userId) {
        if (userId == null) {
            return false;
        }
        Deque<Instant> history = sends.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minus(WINDOW);
        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst().isBefore(cutoff)) {
                history.pollFirst();
            }
            if (history.size() >= LIMIT) {
                return false;
            }
            history.addLast(Instant.now());
            return true;
        }
    }

    /** How many more a user may send right now, for the error message. */
    public int remaining(Integer userId) {
        Deque<Instant> history = sends.get(userId);
        if (history == null) {
            return LIMIT;
        }
        Instant cutoff = Instant.now().minus(WINDOW);
        synchronized (history) {
            history.removeIf(sent -> sent.isBefore(cutoff));
            return Math.max(0, LIMIT - history.size());
        }
    }

    public int limit() {
        return LIMIT;
    }
}
