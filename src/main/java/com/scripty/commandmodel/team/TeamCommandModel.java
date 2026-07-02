package com.scripty.commandmodel.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TeamCommandModel {

    private Integer id;

    @NotBlank(message = "You must supply a value for Team Name.")
    @Size(max = 50, message = "Team Name must be no more than 50 characters in length.")
    private String name;

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
}
