/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.service;

import com.scripty.dao.SceneDao;
import com.scripty.dto.Project;
import com.scripty.dto.Scene;
import java.util.List;
import javax.inject.Inject;

/**
 *
 * @author chris
 */
public class SceneServiceImpl implements SceneService {

    SceneDao sceneDao;

    @Inject
    public SceneServiceImpl(SceneDao sceneDao) {
        this.sceneDao = sceneDao;
    }

    @Override
    public Scene create(Scene scene) {
        return sceneDao.create(scene);
    }

    @Override
    public Scene createBelow(Scene scene) {
        return sceneDao.createBelow(scene);
    }

    @Override
    public Scene read(Integer id) {
        return sceneDao.read(id);
    }

    @Override
    public void update(Scene scene) {
        sceneDao.update(scene);
    }

    @Override
    public void delete(Scene scene) {
        sceneDao.delete(scene);
    }

    @Override
    public void moveUp(Scene scene) {
        sceneDao.moveUp(scene);
    }

    @Override
    public void moveDown(Scene scene) {
        sceneDao.moveDown(scene);
    }

    @Override
    public List<Scene> list() {
        return sceneDao.list();
    }

    @Override
    public Scene getPreviousScene(Scene scene) {
        return sceneDao.getPreviousScene(scene);
    }

    @Override
    public Scene getNextScene(Scene scene) {
        return sceneDao.getNextScene(scene);
    }

    @Override
    public List<Scene> getScenesByProject(Project project) {
        return sceneDao.getScenesByProject(project);
    }
    
}
