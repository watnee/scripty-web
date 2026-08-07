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
 * One song editor's undo/redo stacks, persisted so they outlive the HTTP
 * session — {@link ProjectUndoState} for the screenplay, and for exactly the
 * same reason. API clients authenticate per request and keep no session (the
 * iOS client refuses cookies outright), so a session-backed stack was rebuilt
 * empty on every call: the status endpoint answered {@code canUndo: false}
 * forever and the editor's Undo button never came out of its greyed state.
 *
 * <p>Stacks are per (document, edition, user): a member's undo rewinds their
 * own edits, not a collaborator's, and an undo in one edition never applies a
 * snapshot taken in another. Each stack is a JSON array of encoded line
 * snapshots, newest first.
 *
 * <p>Unlike the screenplay's, the edition here is never null and so needs no
 * stand-in key: every path into {@code SongUndoRedoService} resolves one first
 * ({@code SongEditionService.ensureDefaultEdition}), and the service refuses a
 * checkpoint without it.
 */
@Entity
@Table(name = "song_undo_state")
public class SongUndoState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "text_document_id", nullable = false)
    private TextDocument textDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_edition_id", nullable = false)
    private SongEdition songEdition;

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

    public TextDocument getTextDocument() {
        return textDocument;
    }

    public void setTextDocument(TextDocument textDocument) {
        this.textDocument = textDocument;
    }

    public SongEdition getSongEdition() {
        return songEdition;
    }

    public void setSongEdition(SongEdition songEdition) {
        this.songEdition = songEdition;
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
