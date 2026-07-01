/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.commandmodel.project.createproject;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

/**
 *
 * @author chris
 */
public class CreateProjectCommandModel {
    
    @NotBlank(message = "You must supply a value for Title.")
    @Size(max = 100, message = "Title must be no more than 100 characters in length.")
    private String title;
    @Size(max = 50, message = "Team must be no more than 50 characters in length.")
    private String team;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }
}
