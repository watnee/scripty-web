package com.scripty.viewmodel.project.projecttrash;

import java.time.LocalDateTime;

/** One trashed screenplay, with the dates that frame its recovery window. */
public class ProjectTrashItemViewModel {

    private int id;
    private String title;
    private LocalDateTime deletedAt;
    private LocalDateTime purgesAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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
