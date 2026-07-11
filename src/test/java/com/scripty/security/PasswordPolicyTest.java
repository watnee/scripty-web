package com.scripty.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordPolicyTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void wellKnownDefaultsAreWeak() {
        assertTrue(PasswordPolicy.isWeak("admin", "someone"));
        assertTrue(PasswordPolicy.isWeak("Password", "someone"));
        assertTrue(PasswordPolicy.isWeak("changeme", "someone"));
        assertTrue(PasswordPolicy.isWeak("12345678", "someone"));
    }

    @Test
    void blankShortAndUsernamePasswordsAreWeak() {
        assertTrue(PasswordPolicy.isWeak(null, "someone"));
        assertTrue(PasswordPolicy.isWeak("", "someone"));
        assertTrue(PasswordPolicy.isWeak("short", "someone"));
        assertTrue(PasswordPolicy.isWeak("clintwatnee", "ClintWatnee"));
    }

    @Test
    void reasonablePasswordIsNotWeak() {
        assertFalse(PasswordPolicy.isWeak("correct-horse-battery", "admin"));
    }

    @Test
    void deployPasswordRequiresTwelveChars() {
        assertFalse(PasswordPolicy.isStrongDeployPassword("elevenchars", "admin"));
        assertTrue(PasswordPolicy.isStrongDeployPassword("a-much-longer-secret", "admin"));
    }

    @Test
    void detectsWellKnownPasswordFromHash() {
        assertTrue(PasswordPolicy.matchesWellKnownPassword(
                encoder, encoder.encode("admin"), "admin"));
        assertTrue(PasswordPolicy.matchesWellKnownPassword(
                encoder, encoder.encode("clint"), "clint"));
        assertFalse(PasswordPolicy.matchesWellKnownPassword(
                encoder, encoder.encode("correct-horse-battery"), "admin"));
    }

    @Test
    void generatedPasswordsAreLongUniqueAndStrong() {
        String first = PasswordPolicy.generatePassword();
        String second = PasswordPolicy.generatePassword();
        assertEquals(24, first.length());
        assertNotEquals(first, second);
        assertTrue(PasswordPolicy.isStrongDeployPassword(first, "admin"));
    }
}
