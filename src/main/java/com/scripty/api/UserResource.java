package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResource extends RepresentationModel<UserResource> {

    private Integer id;
    private String username;
    private String firstName;
    private String lastName;
    private String team;
    private Boolean admin;
    private Boolean producer;
    private Boolean director;
    private Boolean writer;
    private Boolean actor;
    private Boolean crew;
    private Boolean directorOfPhotography;
    private Boolean enabled;

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

    public Boolean getAdmin() {
        return admin;
    }

    public void setAdmin(Boolean admin) {
        this.admin = admin;
    }

    public Boolean getProducer() {
        return producer;
    }

    public void setProducer(Boolean producer) {
        this.producer = producer;
    }

    public Boolean getDirector() {
        return director;
    }

    public void setDirector(Boolean director) {
        this.director = director;
    }

    public Boolean getWriter() {
        return writer;
    }

    public void setWriter(Boolean writer) {
        this.writer = writer;
    }

    public Boolean getActor() {
        return actor;
    }

    public void setActor(Boolean actor) {
        this.actor = actor;
    }

    public Boolean getCrew() {
        return crew;
    }

    public void setCrew(Boolean crew) {
        this.crew = crew;
    }

    public Boolean getDirectorOfPhotography() {
        return directorOfPhotography;
    }

    public void setDirectorOfPhotography(Boolean directorOfPhotography) {
        this.directorOfPhotography = directorOfPhotography;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
