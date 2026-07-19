package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scripty.viewmodel.song.versionhistory.SongVersionChangeSummary;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of a single saved {@link com.scripty.dto.SongVersion} in a
 * song's version history. Mirrors {@link ProjectVersionResource}, but carries
 * the song's title and line count in place of scene/block/character counts; the
 * snapshot JSON itself is never exposed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.VERSION, collectionRelation = "songVersions")
public class SongVersionResource extends RepresentationModel<SongVersionResource> {

    private Integer id;
    private String label;
    private String title;
    private OffsetDateTime createdAt;
    private boolean autoSave;
    private int lineCount;
    private SongVersionChangeSummary changeSummary;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public SongVersionChangeSummary getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(SongVersionChangeSummary changeSummary) {
        this.changeSummary = changeSummary;
    }
}
