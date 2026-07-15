package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;

/**
 * The signed-in user's own account, served at {@code GET /api/account}.
 *
 * <p>Unlike {@link UserResource} (the admin-only directory view of any user),
 * this is the "me" resource every authenticated client can read. It carries
 * just enough identity to render a user menu — display name and whether the
 * caller is an admin — plus a {@code changePassword} link when self-service
 * password change is available.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResource extends RepresentationModel<AccountResource> {

    private String username;
    private String firstName;
    private String lastName;
    private boolean admin;

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

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }
}
