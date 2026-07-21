package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * One passkey registered to the signed-in user. The credential id is the
 * base64url form the WebAuthn spec uses, and doubles as the resource's
 * identifier; the dates are ISO-8601 so a client can format them itself rather
 * than being handed a pre-formatted string.
 *
 * <p>Registering a new passkey is a browser ceremony handled by Spring
 * Security's WebAuthn filters, so this API offers listing and revoking only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.PASSKEY, collectionRelation = ApiRel.PASSKEYS)
public class PasskeyResource extends RepresentationModel<PasskeyResource> {

    private String credentialId;
    private String label;
    private String created;
    private String lastUsed;

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(String lastUsed) {
        this.lastUsed = lastUsed;
    }
}
