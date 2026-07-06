package com.scripty.viewmodel.project.createproject;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.dto.Team;
import java.util.List;

public class CreateProjectViewModel {
    
    private CreateProjectCommandModel createProjectCommandModel;
    private List<Team> availableTeams;

    public CreateProjectCommandModel getCreateProjectCommandModel() {
        return createProjectCommandModel;
    }

    public void setCreateProjectCommandModel(CreateProjectCommandModel createProjectCommandModel) {
        this.createProjectCommandModel = createProjectCommandModel;
    }

    public List<Team> getAvailableTeams() {
        return availableTeams;
    }

    public void setAvailableTeams(List<Team> availableTeams) {
        this.availableTeams = availableTeams;
    }
}
