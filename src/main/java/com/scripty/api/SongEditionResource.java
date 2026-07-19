package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;

/**
 * HAL representation of one named edition of a song — an alternate set of
 * lyrics, a rewrite, a version cut for a different scene.
 *
 * <p>The song counterpart of {@link ScriptEditionResource}, and the same gap it
 * fills: {@code editionId} has been accepted on song blocks and song version
 * history all along, with no way for a client to learn which ids exist.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SongEditionResource extends RepresentationModel<SongEditionResource> {

    private Integer id;
    private String name;
    private boolean isDefault;
    private boolean isPublished;
    private OffsetDateTime lastEdited;
    private int blockCount;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public OffsetDateTime getLastEdited() {
        return lastEdited;
    }

    public void setLastEdited(OffsetDateTime lastEdited) {
        this.lastEdited = lastEdited;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public void setBlockCount(int blockCount) {
        this.blockCount = blockCount;
    }
}
