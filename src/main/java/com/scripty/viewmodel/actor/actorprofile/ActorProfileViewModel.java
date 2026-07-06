/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.actor.actorprofile;

import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author chris
 */
public class ActorProfileViewModel {
    
    private int id;
    private String first;
    private String last;
    private String phone;
    private String email;
    private boolean hasHeadshot;
    private List<ProjectViewModel> projects = new ArrayList<>();
    private List<AssignedRoleViewModel> assignedRoles = new ArrayList<>();
    private List<AssignedRoleViewModel> auditionRoles = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirst() {
        return first;
    }

    public void setFirst(String first) {
        this.first = first;
    }

    public String getLast() {
        return last;
    }

    public void setLast(String last) {
        this.last = last;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public List<AssignedRoleViewModel> getAuditionRoles() {
        return auditionRoles;
    }

    public void setAuditionRoles(List<AssignedRoleViewModel> auditionRoles) {
        this.auditionRoles = auditionRoles;
    }

}
