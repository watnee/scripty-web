/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.commandmodel.project.editproject;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 *
 * @author chris
 */
public class EditProjectCommandModel {
    
    private Integer id;
    
    @NotBlank(message = "You must supply a value for Title.")
    @Size(max = 100, message = "Title must be no more than 100 characters in length.")
    private String title;
    private List<Integer> teamIds;

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

    public List<Integer> getTeamIds() {
        return teamIds;
    }

    public void setTeamIds(List<Integer> teamIds) {
        this.teamIds = teamIds;
    }

    /**
     * Title-page fields, all optional: a null field leaves the stored value
     * alone. The MVC edit form does not submit them (it has its own title-page
     * page), so that path is unaffected. Limits mirror
     * {@code TitlePageCommandModel}, which persists these.
     */
    @Size(max = 255, message = "Screenplay Title must be no more than 255 characters in length.")
    private String screenplayTitle;

    @Size(max = 255, message = "Writers must be no more than 255 characters in length.")
    private String writers;

    @Size(max = 1000, message = "Contact Information must be no more than 1000 characters in length.")
    private String contactInfo;

    @Size(max = 255, message = "Version must be no more than 255 characters in length.")
    private String screenplayVersion;

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

    /**
     * True when the caller supplied at least one title-page field.
     */
    public boolean hasTitlePageFields() {
        return screenplayTitle != null || writers != null
                || contactInfo != null || screenplayVersion != null;
    }
}
