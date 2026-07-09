package com.scripty.viewmodel.textdocument;

import java.util.ArrayList;
import java.util.List;

public class TextDocumentListViewModel {

    private Integer projectId;
    private String projectTitle;
    private List<TextDocumentViewModel> songs = new ArrayList<>();
    private List<TextDocumentViewModel> drafts = new ArrayList<>();

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

    public boolean isEmpty() {
        return songs.isEmpty() && drafts.isEmpty();
    }
}
