package com.scripty.viewmodel.song.versionhistory;

import java.util.List;

public class SongVersionHistoryViewModel {

    private int documentId;
    private int projectId;
    private String projectTitle;
    private String songTitle;
    private Integer editionId;
    private String editionName;
    private List<SongVersionViewModel> versions = List.of();

    public int getDocumentId() {
        return documentId;
    }

    public void setDocumentId(int documentId) {
        this.documentId = documentId;
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

    public List<SongVersionViewModel> getVersions() {
        return versions;
    }

    public void setVersions(List<SongVersionViewModel> versions) {
        this.versions = versions;
    }
}
