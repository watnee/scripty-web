package com.scripty.api;

/**
 * A password change for the signed-in user. The current password is required
 * even for an account the server has flagged as needing a change — knowing the
 * old one is what makes the new one the account holder's choice.
 */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
