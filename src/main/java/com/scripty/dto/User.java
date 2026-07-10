package com.scripty.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "`user`")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(name = "`password`", nullable = false, length = 100)
    private String password;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "first_name", nullable = false, length = 30)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 30)
    private String lastName;

    @Column(length = 50)
    private String team;

    @Column(length = 100)
    private String email;

    @Column(name = "default_project_id")
    private Integer defaultProjectId;

    @Transient
    private boolean admin;

    @Transient
    private boolean director;
    @Transient
    private boolean producer;
    @Transient
    private boolean writer;
    @Transient
    private boolean actor;
    @Transient
    private boolean crew;
    @Transient
    private boolean directorOfPhotography;
    @Transient
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
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public Integer getDefaultProjectId() {
        return defaultProjectId;
    }

    public void setDefaultProjectId(Integer defaultProjectId) {
        this.defaultProjectId = defaultProjectId;
    }
}
