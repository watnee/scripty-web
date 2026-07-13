package com.scripty.commandmodel.project.titlepage;

import jakarta.validation.constraints.Size;

public class TitlePageCommandModel {
    private Integer id;

    @Size(max = 255, message = "Screenplay Title must be no more than 255 characters in length.")
    private String screenplayTitle;

    @Size(max = 255, message = "Writers must be no more than 255 characters in length.")
    private String writers;

    @Size(max = 1000, message = "Contact Information must be no more than 1000 characters in length.")
    private String contactInfo;

    @Size(max = 255, message = "Version must be no more than 255 characters in length.")
    private String screenplayVersion;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
}
