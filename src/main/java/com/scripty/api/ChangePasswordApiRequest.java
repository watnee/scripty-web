package com.scripty.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSON body for {@code PUT /api/account/password}. Mirrors the validation on
 * the web {@code ChangePasswordCommandModel}; the deeper checks (current
 * password correct, new password not reused, strength policy) live in
 * {@code UserService.changePassword} and surface as 400s.
 */
public class ChangePasswordApiRequest {

    @NotBlank(message = "You must supply your current password.")
    private String currentPassword;

    @NotBlank(message = "You must supply a new password.")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters in length.")
    private String newPassword;

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
}
