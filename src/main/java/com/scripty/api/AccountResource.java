package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * The signed-in user's own account — not an admin's view of someone else's.
 * Carries only what a client needs to decide what to show: who is signed in,
 * whether the server is still insisting on a password change, and whether
 * passkeys are configured at all.
 *
 * <p>What may be done travels as links: {@code changePassword} and, where
 * passkeys are enabled, {@code passkeys}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.ACCOUNT, collectionRelation = ApiRel.ACCOUNT)
public class AccountResource extends RepresentationModel<AccountResource> {

    private String username;
    private String firstName;
    private String lastName;
    private Boolean passwordChangeRequired;
    private Boolean passkeysEnabled;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Boolean getPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public void setPasswordChangeRequired(Boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public Boolean getPasskeysEnabled() {
        return passkeysEnabled;
    }

    public void setPasskeysEnabled(Boolean passkeysEnabled) {
        this.passkeysEnabled = passkeysEnabled;
    }
}
