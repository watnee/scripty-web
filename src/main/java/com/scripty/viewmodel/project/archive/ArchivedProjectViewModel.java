package com.scripty.viewmodel.project.archive;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One screenplay on the archive page.
 *
 * <p>Like {@link com.scripty.viewmodel.project.trash.TrashedProjectViewModel}
 * but with no purge date — nothing expires out of the archive — and with the
 * detail the project list shows, since an archived project can still be opened
 * and worked on rather than only recovered.
 */
public class ArchivedProjectViewModel {

    private final int id;
    private final String title;
    private final LocalDateTime lastEdited;
    private final LocalDateTime archivedAt;
    private final List<String> teams;

    public ArchivedProjectViewModel(int id, String title, LocalDateTime lastEdited,
                                    LocalDateTime archivedAt, List<String> teams) {
        this.id = id;
        this.title = title;
        this.lastEdited = lastEdited;
        this.archivedAt = archivedAt;
        this.teams = teams != null ? teams : List.of();
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getLastEdited() {
        return lastEdited;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public List<String> getTeams() {
        return teams;
    }
}
