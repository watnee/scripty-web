package com.scripty.commandmodel.user.createuser;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public class CreateUserCommandModel {

    @NotBlank(message = "You must supply a value for Username.")
    @Size(max = 20, message = "Username must be no more than 20 characters in length.")
    private String username;
    @NotBlank(message = "You must supply a value for Password.")
    @Size(max = 100, message = "Password must be no more than 100 characters in length.")
    private String password;
    @NotBlank(message = "You must supply a value for First Name.")
    @Size(max = 30, message = "First Name must be no more than 30 characters in length.")
    private String firstName;
    @NotBlank(message = "You must supply a value for Last Name.")
    @Size(max = 30, message = "Last Name must be no more than 30 characters in length.")
    private String lastName;
    private boolean admin;
    private boolean writer;

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

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isWriter() {
        return writer;
    }

    public void setWriter(boolean writer) {
        this.writer = writer;
    }
}
