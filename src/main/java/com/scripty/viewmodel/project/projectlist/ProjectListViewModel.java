/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.project.projectlist;

import java.util.List;

/**
 *
 * @author chris
 */
public class ProjectListViewModel {
    
    private List<ProjectViewModel> projects;

    public List<ProjectViewModel> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectViewModel> projects) {
        this.projects = projects;
    }
    
}
