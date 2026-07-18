package com.scripty.viewmodel.textdocument;

import com.scripty.viewmodel.songblock.SongBlockViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One song rendered as an expandable section on the songs workspace, where the
 * whole project's songs are edited on a single page. Carries everything the
 * stacked block editor needs so the page can be served in one request instead
 * of a round trip per song.
 */
public class SongWorkspacePaneViewModel {

    private Integer id;
    private String title;
    private LocalDateTime updatedAt;
    /** The edition the reader is allowed to see: their default, or the published one. */
    private Integer editionId;
    private String editionName;
    private List<SongBlockViewModel> blocks = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getEditionId() {
        return editionId;
    }

    public void setEditionId(Integer editionId) {
        this.editionId = editionId;
    }

    public String getEditionName() {
        return editionName;
    }

    public void setEditionName(String editionName) {
        this.editionName = editionName;
    }

    public List<SongBlockViewModel> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<SongBlockViewModel> blocks) {
        this.blocks = blocks != null ? blocks : new ArrayList<>();
    }

    /** Line count, shown on the collapsed header so a song reads as non-empty. */
    public int getLineCount() {
        return blocks.size();
    }
}
