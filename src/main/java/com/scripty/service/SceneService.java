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
public interface SceneService {
    
    public Scene create(Scene scene);
    public Scene createBelow(Scene scene);
    public Scene read(Integer id);
    public void update(Scene scene);
    public void delete(Scene scene);
    public void moveUp(Scene scene);
    public void moveDown(Scene scene);
    public List<Scene> list();
    public Scene getPreviousScene(Scene scene);
    public Scene getNextScene(Scene scene);
    public List<Scene> getScenesByProject(Project project);
    
}
