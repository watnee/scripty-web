package com.scripty.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "text_document")
public class TextDocument {

    public static final String TYPE_SONG = "SONG";
    public static final String TYPE_NOTES = "NOTES";
    public static final String TYPE_OTHER = "OTHER";

    public static final Set<String> DOCUMENT_TYPES = Set.of(TYPE_SONG, TYPE_NOTES, TYPE_OTHER);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /**
     * What this song or note is, as against where it is stored.
     *
     * The database id names a row; this names the work. They come apart the
     * moment the same song exists in two places — an account and a device that
     * was signed out when it was written — because each of those numbers its
     * documents from 1 and neither can adopt the other's number. Carried in the
     * project archive, it is what lets a file coming back in say "this is the
     * song you already have" rather than "here is another song", so a lyric
     * keeps its id, its versions and its lines across a sign-out and back.
     *
     * Unique within a project, not globally: it is only ever matched against
     * the documents of the project being written into, and demanding more would
     * mean refusing a file exported from one account and imported into another.
     *
     * Nullable only for rows written before this column existed, which
     * V58 fills in; everything created since gets one in {@link #assignUid()}.
     */
    @Column(name = "uid", length = 64)
    private String uid;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType = TYPE_SONG;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** When set, the document is in the trash: hidden everywhere, restorable until purged. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * When set, the document is archived: kept out of the list but otherwise
     * whole. Unlike {@link #deletedAt} nothing ever expires it, and the document
     * stays openable, exportable and part of a project bundle.
     */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

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

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    /**
     * Gives a new document its {@link #getUid() uid} unless it arrived with one.
     *
     * On the entity rather than in the services that create documents, because
     * there are several of those and a song that reached the database without a
     * uid would look identical to one that has always had it — right up until a
     * sign-out, when it would come back as a second song.
     */
    @PrePersist
    void assignUid() {
        if (uid == null || uid.isBlank()) {
            uid = UUID.randomUUID().toString();
        }
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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public static String typeLabelFor(String type) {
        if (type == null) {
            return "Song";
        }
        return switch (type.toUpperCase()) {
            case TYPE_SONG -> "Song";
            case TYPE_NOTES -> "Notes";
            case TYPE_OTHER -> "Other";
            default -> type;
        };
    }
}
