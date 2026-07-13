package com.scripty.commandmodel.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SendInvitationCommandModel {

    @NotNull(message = "Project is required.")
    private Integer projectId;

    @NotNull(message = "You must choose a team.")
    private Integer teamId;

    @NotBlank(message = "You must supply an email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 100, message = "Email must be no more than 100 characters.")
    private String email;

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
