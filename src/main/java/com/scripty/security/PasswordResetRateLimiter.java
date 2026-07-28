package com.scripty.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Caps how often a password reset may be asked for.
 *
 * <p>Requesting a reset is the one thing an anonymous caller can do that makes
 * the server send mail to an address someone else chose. Left open it is a way
 * to bury a person's inbox — or, once an account is known to exist, to keep
 * minting fresh tokens in the hope one of them is guessed or intercepted.
 *
 * <p>Two windows, because they stop different things. The per-address one is
 * the one that matters to the person being mailed: it is the reason a stranger
 * cannot send you forty reset emails. The per-IP one is the brake on a script
 * walking a list of addresses. Either can refuse on its own.
 *
 * <p>Like {@link InvitationRateLimiter} this is a deliberately simple in-memory
 * sliding window, not a distributed quota: a restart clears it and a second
 * instance keeps its own count. That is the right trade for something whose job
 * is to stop a runaway loop, and it is honest about not being more than that.
 *
 * <p>Nothing here may change what the caller is told. A refusal that looked
 * different from a send would answer the question the whole flow is built to
 * avoid answering — whether that address has an account. Callers skip the send
 * and return the same generic message.
 */
@Component
public class PasswordResetRateLimiter {

    /**
     * Resets one address may be asked for within the window. Three is room for
     * "it didn't arrive, try again" twice over, and the tokens are single-use
     * anyway — asking again invalidates the last one.
     */
    static final int PER_EMAIL_LIMIT = 3;
    static final Duration PER_EMAIL_WINDOW = Duration.ofMinutes(15);

    /**
     * Requests one client address may make within the window, across every
     * email address it names. Well above a person fumbling their own address,
     * far below anything worth calling a sweep.
     */
    static final int PER_IP_LIMIT = 15;
    static final Duration PER_IP_WINDOW = Duration.ofHours(1);

    /**
     * How many addresses either map will track before it is swept.
     *
     * <p>{@link InvitationRateLimiter} keys on a user id, so its map is bounded
     * by how many accounts exist. This one keys on whatever string an anonymous
     * caller put in a form, which is bounded by nothing — a loop over made-up
     * addresses would otherwise turn the brake into the leak. Reaching the cap
     * drops every history whose window has already run out, which is enough
     * unless the cap itself is being attacked; see {@link #sweep}.
     */
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Deque<Instant>> byEmail = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> byIp = new ConcurrentHashMap<>();

    /**
     * Records an attempt and reports whether the email should actually be sent.
     *
     * @param email the address the caller asked about; may be null or blank
     * @param clientIp the caller's address, or null when it cannot be determined
     * @return true when this attempt is within both allowances
     */
    public boolean tryAcquire(String email, String clientIp) {
        // Both are recorded even when the first one refuses. An attempt that was
        // turned away is still an attempt, and letting a blocked address go
        // unrecorded against the IP would make the second window easy to dodge.
        boolean emailOk = record(byEmail, normalizeEmail(email), PER_EMAIL_LIMIT, PER_EMAIL_WINDOW);
        boolean ipOk = record(byIp, normalizeIp(clientIp), PER_IP_LIMIT, PER_IP_WINDOW);
        return emailOk && ipOk;
    }

    private static String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            // Everything unusable shares one bucket rather than none: a caller
            // POSTing blanks in a loop is still a caller to slow down.
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeIp(String clientIp) {
        return StringUtils.hasText(clientIp) ? clientIp.trim() : "";
    }

    private static boolean record(Map<String, Deque<Instant>> histories, String key,
                                  int limit, Duration window) {
        if (histories.size() >= MAX_TRACKED && !histories.containsKey(key)) {
            sweep(histories, window);
        }
        Deque<Instant> history = histories.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst().isBefore(cutoff)) {
                history.pollFirst();
            }
            boolean allowed = history.size() < limit;
            history.addLast(now);
            // Without this an address hammered forever grows an unbounded deque.
            // The window already decides what counts; this only bounds the memory.
            while (history.size() > limit) {
                history.pollFirst();
            }
            return allowed;
        }
    }

    /**
     * Drops every history that has nothing left inside the window.
     *
     * <p>Worst case — enough distinct keys inside one window to fill the map —
     * this frees nothing and the map is allowed to keep growing. That is the
     * deliberate choice: the alternative is evicting a live history, which
     * hands the attacker a way to clear their own count. Growth under that load
     * is bounded by how fast requests arrive, and the per-IP window is what
     * actually slows it down.
     */
    private static void sweep(Map<String, Deque<Instant>> histories, Duration window) {
        Instant cutoff = Instant.now().minus(window);
        histories.values().removeIf(history -> {
            synchronized (history) {
                Instant last = history.peekLast();
                return last == null || last.isBefore(cutoff);
            }
        });
    }
}
