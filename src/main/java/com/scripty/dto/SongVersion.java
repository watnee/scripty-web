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
 * A restorable snapshot of a song, mirroring {@link ProjectVersion} for the
 * screenplay. Songs are edited as {@link SongBlock} lines whose undo/redo stacks
 * are session-scoped, so these snapshots are what survive a closed tab.
 */
@Entity
@Table(name = "song_version")
public class SongVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "text_document_id", nullable = false)
    private TextDocument textDocument;

    @Column(nullable = false)
    private String label;

    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT", nullable = false)
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
