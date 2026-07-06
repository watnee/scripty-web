package com.scripty.viewmodel.project.editproject;

import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.dto.Team;
import java.util.List;

public class EditProjectViewModel {
    
    private int id;
    private EditProjectCommandModel editProjectCommandModel;
    private List<Team> availableTeams;

    public EditProjectCommandModel getEditProjectCommandModel() {
        return editProjectCommandModel;
    }

    public void setEditProjectCommandModel(EditProjectCommandModel editProjectCommandModel) {
        this.editProjectCommandModel = editProjectCommandModel;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<Team> getAvailableTeams() {
        return availableTeams;
    }

    public void setAvailableTeams(List<Team> availableTeams) {
        this.availableTeams = availableTeams;
    }
}
