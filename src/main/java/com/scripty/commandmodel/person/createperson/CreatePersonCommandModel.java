/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.commandmodel.person.createperson;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

/**
 *
 * @author chris
 */
public class CreatePersonCommandModel {
    
    @NotBlank(message = "You must supply a value for Name.")
    @Size(max = 60, message = "Name must be no more than 60 characters in length.")
    private String name;
    @NotBlank(message = "You must supply a value for Username.")
    @Size(max = 60, message = "Full Name must be no more than 60 characters in length.")
    private String fullName;
    
    private Integer actorId;
    private Integer projectId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Integer getActorId() {
        return actorId;
    }

    public void setActorId(Integer actorId) {
        this.actorId = actorId;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }
    
}
