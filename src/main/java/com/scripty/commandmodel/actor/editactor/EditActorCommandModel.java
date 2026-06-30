/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.commandmodel.actor.editactor;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

/**
 *
 * @author chris
 */
public class EditActorCommandModel {
    
    private Integer id;
    
    @NotBlank(message = "You must supply a value for First Name.")
    @Size(max = 30, message = "First Name must be no more than 30 characters in length.")
    private String first;
    @Size(max = 30, message = "Last Name must be no more than 30 characters in length.")
    private String last;
    @Size(max = 20, message = "Phone must be no more than 20 characters in length.")
    private String phone;
    @Size(max = 30, message = "Email must be no more than 30 characters in length.")
    private String email;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getFirst() {
        return first;
    }

    public void setFirst(String first) {
        this.first = first;
    }

    public String getLast() {
        return last;
    }

    public void setLast(String last) {
        this.last = last;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    
}
