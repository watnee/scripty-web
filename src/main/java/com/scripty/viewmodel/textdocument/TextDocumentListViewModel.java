package com.scripty.viewmodel.textdocument;

import java.util.ArrayList;
import java.util.List;

public class TextDocumentListViewModel {

    private Integer projectId;
    private String projectTitle;
    private List<TextDocumentViewModel> songs = new ArrayList<>();
    private List<TextDocumentViewModel> drafts = new ArrayList<>();
    /** How many songs are sitting in the trash, so the list can offer a way back to them. */
    private int trashedSongCount;
    /** How many notes are sitting in the trash. */
    private int trashedDraftCount;

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public List<TextDocumentViewModel> getSongs() {
        return songs;
    }

    public void setSongs(List<TextDocumentViewModel> songs) {
        this.songs = songs != null ? songs : new ArrayList<>();
    }

    public List<TextDocumentViewModel> getDrafts() {
        return drafts;
    }

    public void setDrafts(List<TextDocumentViewModel> drafts) {
        this.drafts = drafts != null ? drafts : new ArrayList<>();
    }

    public int getTrashedSongCount() {
        return trashedSongCount;
    }

    public void setTrashedSongCount(int trashedSongCount) {
        this.trashedSongCount = trashedSongCount;
    }

    public int getTrashedDraftCount() {
        return trashedDraftCount;
    }

    public void setTrashedDraftCount(int trashedDraftCount) {
        this.trashedDraftCount = trashedDraftCount;
    }

    public boolean isEmpty() {
        return songs.isEmpty() && drafts.isEmpty();
    }
}
