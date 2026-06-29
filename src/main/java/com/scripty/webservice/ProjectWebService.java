/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.webservice;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.dto.Project;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;

/**
 *
 * @author chris
 */
public interface ProjectWebService {
    
    public ProjectListViewModel getProjectListViewModel();
    public ProjectProfileViewModel getProjectProfileViewModel(Integer id);

    public CreateProjectViewModel getCreateProjectViewModel();
    public EditProjectViewModel getEditProjectViewModel(Integer id);

    public Project saveCreateProjectCommandModel(CreateProjectCommandModel createProjectCommandModel);
    public Project saveEditProjectCommandModel(EditProjectCommandModel editProjectCommandModel);

    public Project deleteProject(Integer id);
    
}
