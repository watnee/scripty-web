package com.scripty.commandmodel.textdocument;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TextDocumentCommandModel {

    private Integer id;

    @NotNull(message = "Project is required.")
    private Integer projectId;

    @NotBlank(message = "You must supply a title.")
    @Size(max = 200, message = "Title must be 200 characters or fewer.")
    private String title;

    @NotBlank(message = "You must choose a type.")
    private String documentType;

    @Size(max = 200_000, message = "Content must be 200,000 characters or fewer.")
    private String content;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
