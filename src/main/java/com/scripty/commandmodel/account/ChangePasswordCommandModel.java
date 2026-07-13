package com.scripty.commandmodel.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordCommandModel {

    @NotBlank(message = "You must supply your current password.")
    private String currentPassword;

    @NotBlank(message = "You must supply a new password.")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters in length.")
    private String newPassword;

    @NotBlank(message = "You must confirm your new password.")
    private String confirmPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
