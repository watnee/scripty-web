package com.scripty.viewmodel.scene.allscenes;

import com.scripty.viewmodel.scene.sceneprofile.SceneProfileViewModel;
import java.util.List;

public class AllScenesViewModel {

    private int projectId;
    private String projectTitle;
    private List<SceneProfileViewModel> scenes;

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public List<SceneProfileViewModel> getScenes() {
        return scenes;
    }

    public void setScenes(List<SceneProfileViewModel> scenes) {
        this.scenes = scenes;
    }
}
