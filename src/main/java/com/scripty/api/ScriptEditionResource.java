package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of one named edition of a screenplay — a shooting draft, a
 * table read, a production revision.
 *
 * <p>The API has accepted {@code editionId} as a query parameter on blocks,
 * imports and version history all along. This is the resource that lets a
 * client find out which ids exist, which is why the parameter was previously
 * unusable from anything but the web app, where the id came from the session.
 *
 * <p>{@code isDefault} is the edition opened when none is named;
 * {@code isPublished} is the one view-only readers see. They are independent:
 * a writer can work in a draft while readers stay on the last published cut.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.EDITION, collectionRelation = ApiRel.EDITIONS)
public class ScriptEditionResource extends RepresentationModel<ScriptEditionResource> {

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
