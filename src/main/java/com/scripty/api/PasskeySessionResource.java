package com.scripty.api;

/**
 * What a successful passkey sign-in hands the native client: who it is now
 * signed in as, and the bearer token that stands in for the password it never
 * had. The raw token appears here once and is stored hashed.
 */
public class PasskeySessionResource {

    private String username;
    private String token;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
