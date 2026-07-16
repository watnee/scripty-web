package com.scripty.viewmodel.song.versionhistory;

import java.util.ArrayList;
import java.util.List;

/**
 * What changed between two song snapshots. The screenplay's
 * {@link com.scripty.viewmodel.project.versionhistory.VersionChangeSummary}
 * counts scenes and characters; a song only has a title and ordered lyric lines.
 */
public class SongVersionChangeSummary {

    private static final int MAX_DETAILS = 5;

    private int linesAdded;
    private int linesRemoved;
    private int linesEdited;
    private boolean titleChanged;
    private final List<String> details = new ArrayList<>();

    public int getLinesAdded() {
        return linesAdded;
    }

    public void setLinesAdded(int linesAdded) {
        this.linesAdded = linesAdded;
    }

    public int getLinesRemoved() {
        return linesRemoved;
    }

    public void setLinesRemoved(int linesRemoved) {
        this.linesRemoved = linesRemoved;
    }

    public int getLinesEdited() {
        return linesEdited;
    }

    public void setLinesEdited(int linesEdited) {
        this.linesEdited = linesEdited;
    }

    public boolean isTitleChanged() {
        return titleChanged;
    }

    public void setTitleChanged(boolean titleChanged) {
        this.titleChanged = titleChanged;
    }

    public List<String> getDetails() {
        return details;
    }

    public void addDetail(String detail) {
        details.add(detail);
    }

    public boolean hasChanges() {
        return linesAdded > 0 || linesRemoved > 0 || linesEdited > 0 || titleChanged;
    }

    public List<String> getVisibleDetails() {
        if (details.size() <= MAX_DETAILS) {
            return details;
        }
        List<String> visible = new ArrayList<>(details.subList(0, MAX_DETAILS));
        visible.add("and " + (details.size() - MAX_DETAILS) + " more changes");
        return visible;
    }

    public String getCompactSummary() {
        if (!hasChanges()) {
            return "No changes";
        }
        List<String> parts = new ArrayList<>();
        if (linesAdded > 0) {
            parts.add("+" + linesAdded + " line" + plural(linesAdded));
        }
        if (linesRemoved > 0) {
            parts.add("-" + linesRemoved + " line" + plural(linesRemoved));
        }
        if (linesEdited > 0) {
            parts.add(linesEdited + " line edit" + plural(linesEdited));
        }
        if (titleChanged) {
            parts.add("title");
        }
        return String.join(", ", parts);
    }

    private static String plural(int count) {
        return count == 1 ? "" : "s";
    }
}
