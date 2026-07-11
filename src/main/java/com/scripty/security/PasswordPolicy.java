package com.scripty.security;

import java.security.SecureRandom;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password rules for accounts that guard the deployment: rejects the well-known
 * defaults this project used to ship with, and generates replacement passwords
 * for the startup credential guard.
 */
public final class PasswordPolicy {

    /** Minimum for interactively chosen passwords (matches the change-password form). */
    public static final int MIN_LENGTH = 8;

    /** Minimum for deploy-time secrets supplied via ADMIN_PASSWORD. */
    public static final int MIN_DEPLOY_LENGTH = 12;

    private static final int GENERATED_LENGTH = 24;

    /** No ambiguous characters (0/O, 1/l/I) so a password read from logs types cleanly. */
    private static final String GENERATED_ALPHABET =
            "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final List<String> WELL_KNOWN = List.of(
            "admin", "password", "changeme", "letmein", "scripty",
            "123456", "12345678", "qwerty", "welcome");

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    /** Weak = blank, too short, a well-known default, or the username itself. */
    public static boolean isWeak(String rawPassword, String username) {
        if (rawPassword == null || rawPassword.isBlank() || rawPassword.length() < MIN_LENGTH) {
            return true;
        }
        if (username != null && rawPassword.equalsIgnoreCase(username)) {
            return true;
        }
        return WELL_KNOWN.stream().anyMatch(rawPassword::equalsIgnoreCase);
    }

    /** Suitable as a deploy-time admin secret. */
    public static boolean isStrongDeployPassword(String rawPassword, String username) {
        return rawPassword != null
                && rawPassword.length() >= MIN_DEPLOY_LENGTH
                && !isWeak(rawPassword, username);
    }

    /**
     * True when the stored bcrypt hash corresponds to a well-known default or the
     * username. Used at startup to find accounts still on seeded credentials.
     */
    public static boolean matchesWellKnownPassword(PasswordEncoder encoder, String passwordHash,
            String username) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return true;
        }
        for (String candidate : WELL_KNOWN) {
            if (encoder.matches(candidate, passwordHash)) {
                return true;
            }
        }
        return username != null && !username.isBlank() && encoder.matches(username, passwordHash);
    }

    public static String generatePassword() {
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(GENERATED_ALPHABET.charAt(RANDOM.nextInt(GENERATED_ALPHABET.length())));
        }
        return sb.toString();
    }
}
