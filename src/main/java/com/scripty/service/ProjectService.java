package com.scripty.service;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.commandmodel.project.titlepage.TitlePageCommandModel;
import com.scripty.dto.Project;
import com.scripty.dto.Scene;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;

public interface ProjectService {

    Project read(Integer id);
    Project getProjectByScene(Scene scene);

    ProjectListViewModel getProjectListViewModel();
    ProjectListViewModel getProjectListViewModel(String userTeam);
    ProjectProfileViewModel getProjectProfileViewModel(Integer id);

    CreateProjectViewModel getCreateProjectViewModel();
    EditProjectViewModel getEditProjectViewModel(Integer id);

    Project saveCreateProjectCommandModel(CreateProjectCommandModel createProjectCommandModel);
    Project saveEditProjectCommandModel(EditProjectCommandModel editProjectCommandModel);
    TitlePageCommandModel getTitlePageCommandModel(Integer id);
    Project saveTitlePageCommandModel(TitlePageCommandModel titlePageCommandModel);

    Project deleteProject(Integer id);
}
