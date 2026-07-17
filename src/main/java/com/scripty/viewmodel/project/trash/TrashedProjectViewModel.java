package com.scripty.viewmodel.project.trash;

import java.time.LocalDateTime;

public class TrashedProjectViewModel {

    private final int id;
    private final String title;
    private final LocalDateTime deletedAt;
    private final LocalDateTime purgeAt;
    private final String deletedAgo;
    private final long daysUntilPurge;

    public TrashedProjectViewModel(int id, String title, LocalDateTime deletedAt, LocalDateTime purgeAt,
                                   String deletedAgo, long daysUntilPurge) {
        this.id = id;
        this.title = title;
        this.deletedAt = deletedAt;
        this.purgeAt = purgeAt;
        this.deletedAgo = deletedAgo;
        this.daysUntilPurge = daysUntilPurge;
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

    /** Human-friendly relative deletion time, e.g. "3 days ago". */
    public String getDeletedAgo() {
        return deletedAgo;
    }

    /** Whole days left before automatic purge; 0 means it is due imminently. */
    public long getDaysUntilPurge() {
        return daysUntilPurge;
    }

    /** True when the project is within the last few days of its recovery window. */
    public boolean isExpiringSoon() {
        return daysUntilPurge <= 3;
    }
}
