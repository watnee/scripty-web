package com.scripty.viewmodel.project.trash;

import java.time.LocalDateTime;

public class TrashedProjectViewModel {

    private final int id;
    private final String title;
    private final LocalDateTime deletedAt;
    private final LocalDateTime purgeAt;

    public TrashedProjectViewModel(int id, String title, LocalDateTime deletedAt, LocalDateTime purgeAt) {
        this.id = id;
        this.title = title;
        this.deletedAt = deletedAt;
        this.purgeAt = purgeAt;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /** When the purge job will hard-delete this project. */
    public LocalDateTime getPurgeAt() {
        return purgeAt;
    }
}
