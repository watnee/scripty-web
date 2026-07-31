package com.scripty.viewmodel.textdocument;

import java.time.LocalDateTime;

/**
 * One note rendered as an expandable section on the notes workspace, where the
 * whole project's notes are edited on a single page.
 *
 * <p>The counterpart of {@link SongWorkspacePaneViewModel}, and deliberately a
 * much smaller thing. A song is an ordered list of lyric blocks belonging to an
 * edition, so its pane has to carry blocks and the edition they came from. A
 * note is a title and its text — there is nothing else to bring, and pretending
 * otherwise would mean inventing structure the writer never asked for.
 */
public class NoteWorkspacePaneViewModel {

    private Integer id;
    private String title;
    private String content;
    private LocalDateTime updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * How long this note is, for the line the header shows when it is closed —
     * the notes workspace's answer to the song pane's line count. Counts words
     * rather than lines: a note is prose, and its paragraphs wrap.
     */
    public int getWordCount() {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.trim().split("\\s+").length;
    }
}
