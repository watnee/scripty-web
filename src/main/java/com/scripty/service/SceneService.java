package com.scripty.service;

import com.scripty.commandmodel.scene.createscene.CreateSceneCommandModel;
import com.scripty.commandmodel.scene.createscenebelow.CreateSceneBelowCommandModel;
import com.scripty.commandmodel.scene.editscene.EditSceneCommandModel;
import com.scripty.dto.Scene;
import com.scripty.viewmodel.scene.allscenes.AllScenesViewModel;
import com.scripty.viewmodel.scene.createscene.CreateSceneViewModel;
import com.scripty.viewmodel.scene.createscenebelow.CreateSceneBelowViewModel;
import com.scripty.viewmodel.scene.editscene.EditSceneViewModel;
import com.scripty.viewmodel.scene.sceneprofile.SceneProfileViewModel;

public interface SceneService {

    Scene read(Integer id);

    SceneProfileViewModel getSceneProfileViewModel(Integer id);
    AllScenesViewModel getAllScenesViewModel(Integer projectId);

    CreateSceneViewModel getCreateSceneViewModel(Integer projectId);
    CreateSceneBelowViewModel getCreateSceneBelowViewModel(Integer id);
    EditSceneViewModel getEditSceneViewModel(Integer id);

    Scene saveCreateSceneCommandModel(CreateSceneCommandModel createSceneCommandModel);
    Scene saveCreateSceneBelowCommandModel(CreateSceneBelowCommandModel createSceneBelowCommandModel);
    Scene saveEditSceneCommandModel(EditSceneCommandModel editSceneCommandModel);

    Scene deleteScene(Integer id);
    Scene moveSceneUp(Integer id);
    Scene moveSceneDown(Integer id);
}
