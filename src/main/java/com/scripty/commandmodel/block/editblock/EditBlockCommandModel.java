package com.scripty.commandmodel.block.editblock;

import jakarta.validation.constraints.NotBlank;

public class EditBlockCommandModel {

    private Integer id;

    @NotBlank(message = "You must supply a value for Content.")
    private String content;

    private Integer personId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

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

    private String tags;

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
