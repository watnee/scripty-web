package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of a project text document — a song (lyrics) or a note
 * (draft). The web app manages these under Songs / Notes; this is the REST
 * counterpart the iPad client follows.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.DOCUMENT, collectionRelation = ApiRel.DOCUMENTS)
public class TextDocumentResource extends RepresentationModel<TextDocumentResource> {

    private Integer id;
    /**
     * What this song or note is, across every place it is kept — see
     * {@link com.scripty.dto.TextDocument#getUid()}.
     *
     * Published because a client can hold the same song in two workspaces at
     * once: one on a device that was signed out when it was written, one here.
     * The ids differ and always will, so this is the only thing that lets it say
     * "the song I was in is that one" after a sign-in or a sign-out.
     */
    private String uid;
    private Integer projectId;
    private String projectTitle;
    private String title;
    private String documentType;
    private String documentTypeLabel;
    private String content;
    private String preview;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    /**
     * When this was put aside, or null — omitted from the JSON entirely for
     * anything in the list. An archived document is still fetched, read and
     * written by id, so a client editing one has no other way to know it is
     * looking at something the list no longer shows.
     */
    private OffsetDateTime archivedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
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

    public String getDocumentTypeLabel() {
        return documentTypeLabel;
    }

    public void setDocumentTypeLabel(String documentTypeLabel) {
        this.documentTypeLabel = documentTypeLabel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }
}
