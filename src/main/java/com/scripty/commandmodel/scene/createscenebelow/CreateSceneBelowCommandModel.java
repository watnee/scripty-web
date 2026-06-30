/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.commandmodel.scene.createscenebelow;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

/**
 *
 * @author chris
 */
public class CreateSceneBelowCommandModel {
    
    private Integer id;
    
    @NotBlank(message = "You must supply a value for Name.")
    @Size(max = 255, message = "Name must be no more than 255 characters in length.")
    private String name;
    
    private Integer projectId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }
    
}
