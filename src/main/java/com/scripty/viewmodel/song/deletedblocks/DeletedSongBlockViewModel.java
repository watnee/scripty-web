package com.scripty.viewmodel.song.deletedblocks;

import java.time.LocalDateTime;

/**
 * One soft-deleted lyric line shown in a song's "recently deleted lines"
 * recovery view. Carries enough to preview the line, show when it was deleted
 * and when it will be purged, and drive the restore / delete-forever actions.
 */
public class DeletedSongBlockViewModel {

    private Integer id;
    private String content;
    private String highlight;
    private LocalDateTime deletedAt;
    private LocalDateTime purgesAt;

    public DeletedSongBlockViewModel() {
    }

    public DeletedSongBlockViewModel(Integer id,
                                     String content,
                                     String highlight,
                                     LocalDateTime deletedAt,
                                     LocalDateTime purgesAt) {
        this.id = id;
        this.content = content;
        this.highlight = highlight;
        this.deletedAt = deletedAt;
        this.purgesAt = purgesAt;
    }

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

    /** True when the line had no visible text, so the view can label it "Empty line". */
    public boolean isBlank() {
        return content == null || content.trim().isEmpty();
    }

    public String getHighlight() {
        return highlight;
    }

    public void setHighlight(String highlight) {
        this.highlight = highlight;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public LocalDateTime getPurgesAt() {
        return purgesAt;
    }

    public void setPurgesAt(LocalDateTime purgesAt) {
        this.purgesAt = purgesAt;
    }
}
