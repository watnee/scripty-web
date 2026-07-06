package com.scripty.commandmodel.block.createblock;

import jakarta.validation.constraints.NotBlank;

public class CreateBlockCommandModel {

    @NotBlank(message = "You must supply a value for Content.")
    private String content;

    private Integer personId;
    private Integer projectId;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPersonId() {
        return personId;
    }

    public void setPersonId(Integer personId) {
        this.personId = personId;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }
}
