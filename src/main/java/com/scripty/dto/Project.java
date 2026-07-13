package com.scripty.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "screenplay_title")
    private String screenplayTitle;

    private String writers;

    @Column(name = "contact_info", length = 1000)
    private String contactInfo;

    @Column(name = "screenplay_version", length = 255)
    private String screenplayVersion;

    @Column(name = "last_edited")
    private LocalDateTime lastEdited;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "project_team",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
    private List<Team> teams = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getLastEdited() {
        return lastEdited;
    }

    public void setLastEdited(LocalDateTime lastEdited) {
        this.lastEdited = lastEdited;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
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

    public String getScreenplayVersion() {
        return screenplayVersion;
    }

    public void setScreenplayVersion(String screenplayVersion) {
        this.screenplayVersion = screenplayVersion;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public void setTeams(List<Team> teams) {
        this.teams = teams != null ? teams : new ArrayList<>();
    }

    public boolean isAssignedToTeam(Team team) {
        if (team == null) {
            return false;
        }
        for (Team assignedTeam : teams) {
            if (team.getId().equals(assignedTeam.getId())) {
                return true;
            }
        }
        return false;
    }

    public List<String> getTeamNames() {
        return teams.stream()
                .map(Team::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    public List<Team> getSortedTeams() {
        return teams.stream()
                .sorted(Comparator.comparing(Team::getName))
                .collect(Collectors.toList());
    }
}
