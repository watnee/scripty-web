/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.commandmodel.project.createproject;

import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.NotEmpty;

/**
 *
 * @author chris
 */
public class CreateProjectCommandModel {
    
    @NotEmpty(message = "You must supply a value for Title.")
    @Length(max = 100, message = "Title must be no more than 100 characters in length.")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    
}
