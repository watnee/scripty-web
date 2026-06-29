/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.webservice;

import com.scripty.commandmodel.scene.createscene.CreateSceneCommandModel;
import com.scripty.commandmodel.scene.createscenebelow.CreateSceneBelowCommandModel;
import com.scripty.commandmodel.scene.editscene.EditSceneCommandModel;
import com.scripty.dto.Scene;
import com.scripty.viewmodel.scene.allscenes.AllScenesViewModel;
import com.scripty.viewmodel.scene.createscene.CreateSceneViewModel;
import com.scripty.viewmodel.scene.createscenebelow.CreateSceneBelowViewModel;
import com.scripty.viewmodel.scene.editscene.EditSceneViewModel;
import com.scripty.viewmodel.scene.sceneprofile.SceneProfileViewModel;

/**
 *
 * @author chris
 */
public interface SceneWebService {
    
    public SceneProfileViewModel getSceneProfileViewModel(Integer id);
    public AllScenesViewModel getAllScenesViewModel(Integer projectId);

    public CreateSceneViewModel getCreateSceneViewModel(Integer projectId);
    public CreateSceneBelowViewModel getCreateSceneBelowViewModel(Integer id);
    public EditSceneViewModel getEditSceneViewModel(Integer id);

    public Scene saveCreateSceneCommandModel(CreateSceneCommandModel createSceneCommandModel);
    public Scene saveCreateSceneBelowCommandModel(CreateSceneBelowCommandModel createSceneBelowCommandModel);
    public Scene saveEditSceneCommandModel(EditSceneCommandModel editSceneCommandModel);

    public Scene deleteScene(Integer id);
    public Scene moveSceneUp(Integer id);
    public Scene moveSceneDown(Integer id);
    
}
