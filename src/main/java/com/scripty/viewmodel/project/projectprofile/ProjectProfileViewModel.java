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
    private List<String> teams;
    private java.time.LocalDateTime lastEdited;
    private String screenplayTitle;
    private String writers;
    private String contactInfo;
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

    public List<String> getTeams() {
        return teams;
    }

    public void setTeams(List<String> teams) {
        this.teams = teams;
    }

    public java.time.LocalDateTime getLastEdited() {
        return lastEdited;
    }

    public void setLastEdited(java.time.LocalDateTime lastEdited) {
        this.lastEdited = lastEdited;
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

    public String getScreenplayTitle() {
        return screenplayTitle;
    }

    public void setScreenplayTitle(String screenplayTitle) {
        this.screenplayTitle = screenplayTitle;
    }

    public String getWriters() {
        return writers;
    }

    public void setWriters(String writers) {
        this.writers = writers;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }
}
