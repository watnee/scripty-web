package com.scripty.commandmodel.user.createuser;

import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.NotEmpty;

public class CreateUserCommandModel {

    @NotEmpty(message = "You must supply a value for Username.")
    @Length(max = 20, message = "Username must be no more than 20 characters in length.")
    private String username;
    @NotEmpty(message = "You must supply a value for Password.")
    @Length(max = 100, message = "Password must be no more than 100 characters in length.")
    private String password;
    @NotEmpty(message = "You must supply a value for First Name.")
    @Length(max = 30, message = "First Name must be no more than 30 characters in length.")
    private String firstName;
    @NotEmpty(message = "You must supply a value for Last Name.")
    @Length(max = 30, message = "Last Name must be no more than 30 characters in length.")
    private String lastName;
    private boolean admin;

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
}
