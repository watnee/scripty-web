/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.project.projectlist;

import java.time.LocalDateTime;
import java.util.List;

/**
 *
 * @author chris
 */
public class ProjectViewModel {
    
    private int id;
    private String title;
    private List<ProjectTeamViewModel> teams;
    private LocalDateTime lastEdited;
    private LocalDateTime deletedAt;
    private long daysUntilPurge;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<ProjectTeamViewModel> getTeams() {
        return teams;
    }

    public void setTeams(List<ProjectTeamViewModel> teams) {
        this.teams = teams;
    }

    public LocalDateTime getLastEdited() {
        return lastEdited;
    }

    public void setLastEdited(LocalDateTime lastEdited) {
        this.lastEdited = lastEdited;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public long getDaysUntilPurge() {
        return daysUntilPurge;
    }

    public void setDaysUntilPurge(long daysUntilPurge) {
        this.daysUntilPurge = daysUntilPurge;
    }
}
