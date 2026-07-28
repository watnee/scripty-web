package com.scripty.service;

import com.scripty.dto.ApiToken;
import com.scripty.repository.ApiTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bearer tokens for native clients that signed in with a passkey.
 *
 * <p>A passkey proves who you are without ever giving the client a password,
 * and the API authenticates every request with an Authorization header — so a
 * passkey sign-in mints one of these long-lived tokens instead. Only the
 * SHA-256 of the token is stored; a leaked database does not leak sessions.
 */
@Service
public class ApiTokenService {

    /** 256 bits of randomness, matching the recovery-token strength. */
    private static final int TOKEN_BYTES = 32;

    /** How stale last_used may get before it is worth another write. */
    private static final long TOUCH_INTERVAL_MINUTES = 60;

    private final ApiTokenRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ApiTokenService(ApiTokenRepository repository) {
        this.repository = repository;
    }

    /** Mints a token for the user and returns its raw value — shown once. */
    @Transactional
    public String issue(String username, String label) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiToken token = new ApiToken();
        token.setUsername(username);
        token.setTokenHash(hash(raw));
        token.setLabel(label);
        token.setCreated(LocalDateTime.now());
        repository.save(token);
        return raw;
    }

    /**
     * Resolves a raw token to its username, or null when unknown. Touches
     * last_used at most once an hour so resolution stays read-mostly.
     */
    @Transactional
    public String resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        Optional<ApiToken> found = repository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return null;
        }
        ApiToken token = found.get();
        LocalDateTime now = LocalDateTime.now();
        if (token.getLastUsed() == null
                || token.getLastUsed().isBefore(now.minusMinutes(TOUCH_INTERVAL_MINUTES))) {
            token.setLastUsed(now);
            repository.save(token);
        }
        return token.getUsername();
    }

    /** Revokes the presented token; a no-op when it is already gone. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(repository::delete);
    }

    /**
     * Revokes every token a user holds — what a password reset needs, since the
     * point of a reset is that whoever had the old credentials keeps nothing.
     */
    @Transactional
    public void revokeAll(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        repository.deleteByUsername(username);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
