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
 * A name a writer files some of a project's songs or notes under.
 *
 * <p>Flat, and belonging to one list rather than to the project: see
 * {@code V59__create_text_document_folder.sql} for why both of those are
 * deliberate. A folder holds nothing itself — {@link TextDocument#getFolder()}
 * is the only thing that puts a document in one — so deleting a folder is
 * always safe, and always leaves its documents behind in the list.
 */
@Entity
@Table(name = "text_document_folder")
public class TextDocumentFolder {

    /** The longest name the column will take, and what the services trim to. */
    public static final int NAME_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /**
     * Which list this folder belongs to — {@link TextDocument#TYPE_SONG} or
     * {@link TextDocument#TYPE_NOTES}. Songs and notes are two lists, and a
     * folder is only ever shown in its own.
     */
    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType = TextDocument.TYPE_SONG;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

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

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
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
