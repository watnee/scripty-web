package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of a folder — a heading a project's songs or notes are
 * filed under.
 *
 * <p>Carries no documents of its own. A client already holds the list, and each
 * document says which folder it is in, so embedding them here would send every
 * song twice and give the client two orders to reconcile. What it does carry is
 * {@code documentCount}, so a heading can say "4" without the client counting —
 * and so it can say "empty" for a folder just made.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.FOLDER, collectionRelation = ApiRel.FOLDERS)
public class TextDocumentFolderResource extends RepresentationModel<TextDocumentFolderResource> {

    private Integer id;
    private Integer projectId;
    /** SONG or NOTES — which of the project's two lists this folder is in. */
    private String documentType;
    private String name;
    /** How many of the list's documents are filed here. */
    private Integer documentCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

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

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(Integer documentCount) {
        this.documentCount = documentCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
