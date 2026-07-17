package com.scripty.viewmodel.song.deletedblocks;

import java.util.List;

/**
 * Backing model for the song "recently deleted lines" page, mirroring
 * {@link com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel}
 * so the recovery view can render breadcrumbs alongside the trashed lines.
 */
public class DeletedSongBlocksViewModel {

    private int documentId;
    private int projectId;
    private String projectTitle;
    private String songTitle;
    private List<DeletedSongBlockViewModel> blocks = List.of();

    public int getDocumentId() {
        return documentId;
    }

    public void setDocumentId(int documentId) {
        this.documentId = documentId;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public String getSongTitle() {
        return songTitle;
    }

    public void setSongTitle(String songTitle) {
        this.songTitle = songTitle;
    }

    public List<DeletedSongBlockViewModel> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<DeletedSongBlockViewModel> blocks) {
        this.blocks = blocks;
    }
}
