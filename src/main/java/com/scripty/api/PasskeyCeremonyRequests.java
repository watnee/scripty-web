package com.scripty.api;

import java.util.List;

/**
 * The native WebAuthn ceremony payloads. Field names and base64url encoding
 * mirror the W3C wire format the browser flow uses, so the two flows stay one
 * contract; the {@code challengeId} is the API's replacement for the session
 * the browser flow keeps between options and verification.
 */
public final class PasskeyCeremonyRequests {

    /** Second half of registering a passkey: the authenticator's attestation. */
    public record PasskeyRegistrationRequest(String challengeId, String label,
            PasskeyCredentialPayload credential) {
    }

    /** Second half of a passkey sign-in: the authenticator's assertion. */
    public record PasskeyAssertionRequest(String challengeId, String label,
            PasskeyCredentialPayload credential) {
    }

    /**
     * One credential shape for both ceremonies: registration fills
     * attestationObject/transports, assertion fills authenticatorData,
     * signature and userHandle. All byte fields are base64url strings.
     */
    public record PasskeyCredentialPayload(String id, String rawId, String type,
            PasskeyCredentialResponsePayload response) {
    }

    public record PasskeyCredentialResponsePayload(String clientDataJSON,
            String attestationObject, List<String> transports,
            String authenticatorData, String signature, String userHandle) {
    }

    private PasskeyCeremonyRequests() {
    }
}
