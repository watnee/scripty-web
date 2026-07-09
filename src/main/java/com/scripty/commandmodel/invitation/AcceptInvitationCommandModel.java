package com.scripty.commandmodel.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AcceptInvitationCommandModel {

    @NotBlank(message = "Invitation token is required.")
    private String token;

    @NotBlank(message = "You must supply a username.")
    @Size(max = 20, message = "Username must be no more than 20 characters.")
    private String username;

    @NotBlank(message = "You must supply a password.")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters.")
    private String password;

    @NotBlank(message = "You must supply a first name.")
    @Size(max = 30, message = "First name must be no more than 30 characters.")
    private String firstName;

    @NotBlank(message = "You must supply a last name.")
    @Size(max = 30, message = "Last name must be no more than 30 characters.")
    private String lastName;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
}
