package com.scripty.api;

/**
 * A new password, and the token from the recovery email that says whose it is.
 *
 * <p>No confirmation field: retyping guards against a typo in a field nobody can
 * see, which is a property of the form rather than of the request. A client that
 * wants to ask twice can, and still sends one password.
 */
public record ResetPasswordRequest(String token, String password) {
}
