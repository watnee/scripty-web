package com.scripty.service;

import com.scripty.dto.PasswordRecoveryToken;

public interface PasswordRecoveryService {

    /**
     * Finds a user by email, generates a recovery token, saves it,
     * and sends a recovery link email.
     * To prevent email enumeration, this method must fail silently (returning success)
     * even if no user is found with the given email.
     */
    void sendRecoveryEmail(String email);

    /**
     * Validates that the token exists and has not expired.
     * @return the token if valid, throws IllegalArgumentException if invalid.
     */
    PasswordRecoveryToken validateToken(String token);

    /**
     * Resets the password for the user associated with the token.
     * Checks password strength using PasswordPolicy.
     */
    void resetPassword(String token, String newPassword);
}
