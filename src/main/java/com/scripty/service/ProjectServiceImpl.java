/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.service;

import com.scripty.dao.ProjectDao;
import com.scripty.dto.Project;
import com.scripty.dto.Scene;
import java.util.List;
import javax.inject.Inject;

/**
 *
 * @author chris
 */
public class ProjectServiceImpl implements ProjectService {

    ProjectDao projectDao;

    @Inject
    public ProjectServiceImpl(ProjectDao projectDao) {
        this.projectDao = projectDao;
    }

    @Override
    public Project create(Project project) {
        return projectDao.create(project);
    }

    @Override
    public Project read(Integer id) {
        return projectDao.read(id);
    }

    @Override
    public void update(Project project) {
        projectDao.update(project);
    }

    @Override
    public void delete(Project project) {
        projectDao.delete(project);
    }

    @Override
    public List<Project> list() {
        return projectDao.list();
    }

    @Override
    public Project getProjectByScene(Scene scene) {
        return projectDao.getProjectByScene(scene);
    }
    
}
