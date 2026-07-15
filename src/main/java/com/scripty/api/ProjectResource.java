package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.hateoas.RepresentationModel;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectResource extends RepresentationModel<ProjectResource> {

    private Integer id;
    private String title;
    private String screenplayTitle;
    private String writers;
    private String contactInfo;
    private String screenplayVersion;
    private OffsetDateTime lastEdited;
    private List<String> teams;
    private boolean isDefault;

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

    public OffsetDateTime getLastEdited() {
        return lastEdited;
    }

    public void setLastEdited(OffsetDateTime lastEdited) {
        this.lastEdited = lastEdited;
    }

    public List<String> getTeams() {
        return teams;
    }

    public void setTeams(List<String> teams) {
        this.teams = teams;
    }

    /** Serialized as "default": whether this is the current user's default project. */
    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
