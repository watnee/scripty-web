package com.scripty.viewmodel.textdocument;

import java.util.ArrayList;
import java.util.List;

public class TextDocumentListViewModel {

    private Integer projectId;
    private String projectTitle;
    private List<TextDocumentViewModel> songs = new ArrayList<>();
    private List<TextDocumentViewModel> drafts = new ArrayList<>();
    /**
     * The two lists' folders, each with the documents filed under it.
     *
     * <p>A view of {@link #songs} / {@link #drafts} rather than a second set of
     * documents: everything here is also in the flat list above, which is what
     * the exports, the counts and the REST listing read. Folders are shown even
     * when empty — a folder made a moment ago with nothing in it yet is exactly
     * the folder a writer is about to drag something into.
     */
    private List<TextDocumentFolderViewModel> songFolders = new ArrayList<>();
    private List<TextDocumentFolderViewModel> draftFolders = new ArrayList<>();
    /** What is left over: the documents in no folder at all. */
    private List<TextDocumentViewModel> unfiledSongs = new ArrayList<>();
    private List<TextDocumentViewModel> unfiledDrafts = new ArrayList<>();
    /** How many songs are sitting in the trash, so the list can offer a way back to them. */
    private int trashedSongCount;
    /** How many notes are sitting in the trash. */
    private int trashedDraftCount;
    /** How many songs are archived, so the list can offer a way through to them. */
    private int archivedSongCount;
    /** How many notes are archived. */
    private int archivedDraftCount;
    private boolean retentionUnlimited;

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

    public List<TextDocumentFolderViewModel> getSongFolders() {
        return songFolders;
    }

    public void setSongFolders(List<TextDocumentFolderViewModel> songFolders) {
        this.songFolders = songFolders != null ? songFolders : new ArrayList<>();
    }

    public List<TextDocumentFolderViewModel> getDraftFolders() {
        return draftFolders;
    }

    public void setDraftFolders(List<TextDocumentFolderViewModel> draftFolders) {
        this.draftFolders = draftFolders != null ? draftFolders : new ArrayList<>();
    }

    public List<TextDocumentViewModel> getUnfiledSongs() {
        return unfiledSongs;
    }

    public void setUnfiledSongs(List<TextDocumentViewModel> unfiledSongs) {
        this.unfiledSongs = unfiledSongs != null ? unfiledSongs : new ArrayList<>();
    }

    public List<TextDocumentViewModel> getUnfiledDrafts() {
        return unfiledDrafts;
    }

    public void setUnfiledDrafts(List<TextDocumentViewModel> unfiledDrafts) {
        this.unfiledDrafts = unfiledDrafts != null ? unfiledDrafts : new ArrayList<>();
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

    public int getArchivedSongCount() {
        return archivedSongCount;
    }

    public void setArchivedSongCount(int archivedSongCount) {
        this.archivedSongCount = archivedSongCount;
    }

    public int getArchivedDraftCount() {
        return archivedDraftCount;
    }

    public void setArchivedDraftCount(int archivedDraftCount) {
        this.archivedDraftCount = archivedDraftCount;
    }

    /** True when trashed documents are kept until someone deletes them for good. */
    public boolean isRetentionUnlimited() {
        return retentionUnlimited;
    }

    public void setRetentionUnlimited(boolean retentionUnlimited) {
        this.retentionUnlimited = retentionUnlimited;
    }

    public boolean isEmpty() {
        return songs.isEmpty() && drafts.isEmpty();
    }
}
