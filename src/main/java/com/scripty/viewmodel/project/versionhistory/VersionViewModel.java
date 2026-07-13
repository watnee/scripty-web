package com.scripty.viewmodel.project.versionhistory;

import java.time.LocalDateTime;

public class VersionViewModel {

    private int id;
    private String label;
    private LocalDateTime createdAt;
    private int sceneCount;
    private int blockCount;
    private int characterCount;
    private boolean autoSave;
    private VersionChangeSummary changeSummary;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getSceneCount() {
        return sceneCount;
    }

    public void setSceneCount(int sceneCount) {
        this.sceneCount = sceneCount;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public void setBlockCount(int blockCount) {
        this.blockCount = blockCount;
    }

    public int getCharacterCount() {
        return characterCount;
    }

    public void setCharacterCount(int characterCount) {
        this.characterCount = characterCount;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public VersionChangeSummary getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(VersionChangeSummary changeSummary) {
        this.changeSummary = changeSummary;
    }
}
