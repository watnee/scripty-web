package com.scripty.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One screenplay editor's undo/redo stacks, persisted so they outlive the HTTP
 * session. API clients authenticate per request and so never keep a session;
 * without this the stacks were rebuilt empty on every call and undo could never
 * fire.
 *
 * <p>Stacks are per (project, edition, user): a member's undo rewinds their own
 * edits, not a collaborator's, and an undo in one edition never applies a
 * snapshot taken in another. Each stack is a JSON array of encoded entries,
 * newest first — either a full project snapshot or a lightweight move entry.
 *
 * <p>{@link #editionKey} carries the uniqueness that {@link #edition} cannot:
 * API clients record against a null edition, and SQL treats NULLs as distinct,
 * so a unique constraint over the nullable column would never dedupe those rows.
 * It mirrors the edition id, or {@link #NO_EDITION} when there is none.
 */
@Entity
@Table(name = "project_undo_state")
public class ProjectUndoState {

    /** {@link #editionKey} stand-in for a null edition, which no id ever takes. */
    public static final int NO_EDITION = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edition_id")
    private ScriptEdition edition;

    @Column(name = "edition_key", nullable = false)
    private Integer editionKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "undo_json", columnDefinition = "LONGTEXT", nullable = false)
    private String undoJson;

    @Column(name = "redo_json", columnDefinition = "LONGTEXT", nullable = false)
    private String redoJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public ScriptEdition getEdition() {
        return edition;
    }

    public void setEdition(ScriptEdition edition) {
        this.edition = edition;
    }

    public Integer getEditionKey() {
        return editionKey;
    }

    public void setEditionKey(Integer editionKey) {
        this.editionKey = editionKey;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getUndoJson() {
        return undoJson;
    }

    public void setUndoJson(String undoJson) {
        this.undoJson = undoJson;
    }

    public String getRedoJson() {
        return redoJson;
    }

    public void setRedoJson(String redoJson) {
        this.redoJson = redoJson;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
