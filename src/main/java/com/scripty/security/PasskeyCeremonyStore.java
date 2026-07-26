package com.scripty.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.stereotype.Component;

/**
 * Holds WebAuthn ceremony state between the options request and its
 * verification, keyed by a random challenge id the client echoes back.
 *
 * <p>The browser flow keeps this state in the HTTP session; the native API
 * client sends no cookies (its {@code URLSession} is deliberately ephemeral),
 * so the correlation handle travels in the body instead. Entries are
 * single-use — taking one removes it — and expire after a few minutes, which
 * caps the window an unfinished ceremony can be replayed into. In-memory is
 * enough here for the same reason the session was: a ceremony's two halves
 * land on the same instance within seconds.
 */
@Component
public class PasskeyCeremonyStore {

    /** WebAuthn's recommended ceremony timeout is minutes, not hours. */
    private static final Duration TTL = Duration.ofMinutes(5);

    public record RegistrationCeremony(String username,
            PublicKeyCredentialCreationOptions options, Instant expires) {
    }

    public record AssertionCeremony(PublicKeyCredentialRequestOptions options, Instant expires) {
    }

    private final ConcurrentMap<String, RegistrationCeremony> registrations =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AssertionCeremony> assertions = new ConcurrentHashMap<>();

    public String putRegistration(String username, PublicKeyCredentialCreationOptions options) {
        prune();
        String id = UUID.randomUUID().toString();
        registrations.put(id, new RegistrationCeremony(username, options, Instant.now().plus(TTL)));
        return id;
    }

    /** Removes and returns the ceremony, or null when unknown or expired. */
    public RegistrationCeremony takeRegistration(String challengeId) {
        if (challengeId == null) {
            return null;
        }
        RegistrationCeremony ceremony = registrations.remove(challengeId);
        return ceremony == null || ceremony.expires().isBefore(Instant.now()) ? null : ceremony;
    }

    public String putAssertion(PublicKeyCredentialRequestOptions options) {
        prune();
        String id = UUID.randomUUID().toString();
        assertions.put(id, new AssertionCeremony(options, Instant.now().plus(TTL)));
        return id;
    }

    /** Removes and returns the ceremony, or null when unknown or expired. */
    public AssertionCeremony takeAssertion(String challengeId) {
        if (challengeId == null) {
            return null;
        }
        AssertionCeremony ceremony = assertions.remove(challengeId);
        return ceremony == null || ceremony.expires().isBefore(Instant.now()) ? null : ceremony;
    }

    /** Abandoned ceremonies (options fetched, never verified) must not pile up. */
    private void prune() {
        Instant now = Instant.now();
        registrations.values().removeIf(c -> c.expires().isBefore(now));
        assertions.values().removeIf(c -> c.expires().isBefore(now));
    }
}
