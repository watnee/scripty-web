/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.commandmodel.project.createproject;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

/**
 *
 * @author chris
 */
public class CreateProjectCommandModel {
    
    @NotBlank(message = "You must supply a value for Title.")
    @Size(max = 100, message = "Title must be no more than 100 characters in length.")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    
}
