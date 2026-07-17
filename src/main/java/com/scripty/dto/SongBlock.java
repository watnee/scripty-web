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
 * A single ordered line ("block") of a song's lyrics. Songs are edited as an
 * ordered list of these blocks; the parent {@link TextDocument#getContent()} is
 * kept in sync (blocks joined by newlines) so downstream features
 * (insert-into-script, share, export) keep working unchanged.
 */
@Entity
@Table(name = "song_block")
public class SongBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "text_document_id", nullable = false)
    private TextDocument textDocument;

    /** The song version this block belongs to; see {@link SongEdition}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_edition_id")
    private SongEdition songEdition;

    @Column(name = "`order`", nullable = false)
    private Integer order = 0;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content = "";

    /** Background tint; see {@link Block#HIGHLIGHTS}. Null means no highlight. */
    @Column(name = "highlight")
    private String highlight;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getHighlight() {
        return highlight;
    }

    public void setHighlight(String highlight) {
        this.highlight = highlight;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
