package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scripty.viewmodel.project.versionhistory.VersionChangeSummary;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of a single saved {@link com.scripty.dto.ProjectVersion}
 * in a project's version history. Mirrors the fields the web version-history
 * view shows; the snapshot JSON itself is never exposed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.VERSION, collectionRelation = ApiRel.VERSIONS)
public class ProjectVersionResource extends RepresentationModel<ProjectVersionResource> {

    private Integer id;
    private String label;
    private OffsetDateTime createdAt;
    private boolean autoSave;
    private int sceneCount;
    private int blockCount;
    private int characterCount;
    private VersionChangeSummary changeSummary;

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

    public VersionChangeSummary getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(VersionChangeSummary changeSummary) {
        this.changeSummary = changeSummary;
    }
}
