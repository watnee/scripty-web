package com.scripty.service;

import com.scripty.dto.ApiToken;
import com.scripty.repository.ApiTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and validates the opaque bearer tokens native clients use after a
 * passkey sign-in. Tokens are 256 bits of {@link SecureRandom} entropy encoded
 * base64url; only their SHA-256 hash is stored.
 */
@Service
public class ApiTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Duration TOKEN_TTL = Duration.ofDays(90);
    /** Skip a last-used write unless the stored value is at least this stale. */
    private static final Duration LAST_USED_THROTTLE = Duration.ofHours(1);

    private final ApiTokenRepository repository;

    public ApiTokenService(ApiTokenRepository repository) {
        this.repository = repository;
    }

    /** Mints a new token for {@code username} and returns the plaintext once. */
    @Transactional
    public String issue(String username, String label) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = BASE64URL.encodeToString(raw);

        ApiToken record = new ApiToken();
        record.setTokenHash(sha256(token));
        record.setUsername(username);
        record.setLabel(label);
        record.setCreatedAt(Instant.now());
        record.setExpiresAt(Instant.now().plus(TOKEN_TTL));
        repository.save(record);
        return token;
    }

    /**
     * Returns the username the token authenticates, or empty if the token is
     * unknown or expired. Refreshes {@code last_used_at} at most hourly so
     * per-request validation stays read-mostly.
     */
    @Transactional
    public Optional<String> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(sha256(token))
                .filter(record -> record.getExpiresAt() == null
                        || record.getExpiresAt().isAfter(Instant.now()))
                .map(record -> {
                    touch(record);
                    return record.getUsername();
                });
    }

    private void touch(ApiToken record) {
        Instant lastUsed = record.getLastUsedAt();
        if (lastUsed == null
                || lastUsed.isBefore(Instant.now().minus(LAST_USED_THROTTLE))) {
            record.setLastUsedAt(Instant.now());
            repository.save(record);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
