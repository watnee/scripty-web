package com.scripty.viewmodel.project.edition;

public class ScriptEditionViewModel {

    private int id;
    private String name;
    private boolean isDefault;
    private boolean isPublished;
    private java.time.LocalDateTime lastEdited;
    private int blockCount;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean isPublished() {
        return isPublished;
    }

    public void setPublished(boolean isPublished) {
        this.isPublished = isPublished;
    }

    public java.time.LocalDateTime getLastEdited() {
        return lastEdited;
    }

    public void setLastEdited(java.time.LocalDateTime lastEdited) {
        this.lastEdited = lastEdited;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public void setBlockCount(int blockCount) {
        this.blockCount = blockCount;
    }
}
