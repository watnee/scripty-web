package com.scripty.commandmodel.block.createblockbelow;

import jakarta.validation.constraints.NotBlank;

public class CreateBlockBelowCommandModel {

    private Integer id;

    @NotBlank(message = "You must supply a value for Content.")
    private String content;

    private Integer personId;
    private String type;
    private Integer sourceDocumentId;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(Integer sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }
}
