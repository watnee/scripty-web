/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.actor.editactor;

import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;
import com.scripty.viewmodel.actor.actorprofile.AssignedRoleViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author chris
 */
public class EditActorViewModel {
    
    private int id;
    
    private boolean hasHeadshot;
    
    // Specifically to handle redisplaying when validation errors happen
    private EditActorCommandModel editActorCommandModel;
    private List<ProjectViewModel> projects = new ArrayList<>();
    private List<AssignedRoleViewModel> assignedRoles = new ArrayList<>();

    public EditActorCommandModel getEditActorCommandModel() {
        return editActorCommandModel;
    }

    public void setEditActorCommandModel(EditActorCommandModel editActorCommandModel) {
        this.editActorCommandModel = editActorCommandModel;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isHasHeadshot() {
        return hasHeadshot;
    }

    public void setHasHeadshot(boolean hasHeadshot) {
        this.hasHeadshot = hasHeadshot;
    }

    public List<ProjectViewModel> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectViewModel> projects) {
        this.projects = projects;
    }

    public List<AssignedRoleViewModel> getAssignedRoles() {
        return assignedRoles;
    }

    public void setAssignedRoles(List<AssignedRoleViewModel> assignedRoles) {
        this.assignedRoles = assignedRoles;
    }
    
}
