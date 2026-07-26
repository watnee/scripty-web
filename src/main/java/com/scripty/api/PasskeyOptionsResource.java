package com.scripty.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * First half of a WebAuthn ceremony over the API: the standard options
 * document under {@code publicKey} (the same JSON the browser hands to
 * {@code navigator.credentials}), plus the {@code challengeId} the client
 * echoes back so the verification can find its challenge without a session.
 */
public class PasskeyOptionsResource {

    private String challengeId;
    private JsonNode publicKey;

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public JsonNode getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(JsonNode publicKey) {
        this.publicKey = publicKey;
    }
}
