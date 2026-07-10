package com.scripty.commandmodel.user.edituser;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public class EditUserCommandModel {

    private Integer id;

    @NotBlank(message = "You must supply a value for Username.")
    @Size(max = 20, message = "Username must be no more than 20 characters in length.")
    private String username;
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters in length.")
    private String password;
    @NotBlank(message = "You must supply a value for First Name.")
    @Size(max = 30, message = "First Name must be no more than 30 characters in length.")
    private String firstName;
    @NotBlank(message = "You must supply a value for Last Name.")
    @Size(max = 30, message = "Last Name must be no more than 30 characters in length.")
    private String lastName;
    @Size(max = 50, message = "Team must be no more than 50 characters in length.")
    private String team;
    private boolean admin;
    private boolean director;
    private boolean producer;
    private boolean writer;
    private boolean actor;
    private boolean crew;
    private boolean directorOfPhotography;
    private boolean castingDirector;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        // Blank means "leave unchanged" on edit; treat as null so @Size(min=8) does not fire.
        this.password = (password == null || password.isBlank()) ? null : password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isDirector() {
        return director;
    }

    public void setDirector(boolean director) {
        this.director = director;
    }

    public boolean isProducer() {
        return producer;
    }

    public void setProducer(boolean producer) {
        this.producer = producer;
    }

    public boolean isWriter() {
        return writer;
    }

    public void setWriter(boolean writer) {
        this.writer = writer;
    }

    public boolean isActor() {
        return actor;
    }

    public void setActor(boolean actor) {
        this.actor = actor;
    }

    public boolean isCrew() {
        return crew;
    }

    public void setCrew(boolean crew) {
        this.crew = crew;
    }

    public boolean isDirectorOfPhotography() {
        return directorOfPhotography;
    }

    public void setDirectorOfPhotography(boolean directorOfPhotography) {
        this.directorOfPhotography = directorOfPhotography;
    }

    public boolean isCastingDirector() {
        return castingDirector;
    }

    public void setCastingDirector(boolean castingDirector) {
        this.castingDirector = castingDirector;
    }
}
