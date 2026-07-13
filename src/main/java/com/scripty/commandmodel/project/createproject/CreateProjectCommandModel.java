package com.scripty.commandmodel.project.createproject;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class CreateProjectCommandModel {
    
    @NotBlank(message = "You must supply a value for Title.")
    @Size(max = 100, message = "Title must be no more than 100 characters in length.")
    private String title;
    private List<Integer> teamIds;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Integer> getTeamIds() {
        return teamIds;
    }

    public void setTeamIds(List<Integer> teamIds) {
        this.teamIds = teamIds;
    }
}
