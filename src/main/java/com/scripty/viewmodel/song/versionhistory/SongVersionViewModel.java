package com.scripty.viewmodel.song.versionhistory;

import java.time.LocalDateTime;

public class SongVersionViewModel {

    private int id;
    private String label;
    private String title;
    private LocalDateTime createdAt;
    private int lineCount;
    private boolean autoSave;
    private SongVersionChangeSummary changeSummary;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public SongVersionChangeSummary getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(SongVersionChangeSummary changeSummary) {
        this.changeSummary = changeSummary;
    }
}
