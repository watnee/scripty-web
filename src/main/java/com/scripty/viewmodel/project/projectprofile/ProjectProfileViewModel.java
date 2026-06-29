/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.project.projectprofile;

import java.util.List;

/**
 *
 * @author chris
 */
public class ProjectProfileViewModel {
    
    private int id;
    private String title;
    private List<SceneViewModel> scenes;
    private List<PersonViewModel> persons;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<SceneViewModel> getScenes() {
        return scenes;
    }

    public void setScenes(List<SceneViewModel> scenes) {
        this.scenes = scenes;
    }

    public List<PersonViewModel> getPersons() {
        return persons;
    }

    public void setPersons(List<PersonViewModel> persons) {
        this.persons = persons;
    }
    
}
