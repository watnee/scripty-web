/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.service;

import com.scripty.dto.Project;
import com.scripty.dto.Scene;
import java.util.List;

/**
 *
 * @author chris
 */
public interface ProjectService {
    
    public Project create(Project project);
    public Project read(Integer id);
    public void update(Project project);
    public void delete(Project project);
    public List<Project> list();
    public Project getProjectByScene(Scene scene);
    
}
