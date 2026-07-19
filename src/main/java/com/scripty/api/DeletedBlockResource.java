package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of a screenplay element sitting in the trash.
 *
 * <p>Carries a preview rather than the full content: this is a list you scan to
 * find the thing you deleted by mistake, and restoring brings the element back
 * whole regardless. {@code purgeAt} is included because a deleted element is
 * only recoverable for a while, and a client that cannot say how long is asking
 * the writer to guess.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.DELETED_BLOCK, collectionRelation = ApiRel.DELETED_BLOCKS)
public class DeletedBlockResource extends RepresentationModel<DeletedBlockResource> {

    private Integer id;
    private String preview;
    private boolean empty;
    private String typeLabel;
    private String editionName;
    private String deletedByName;
    private OffsetDateTime deletedAt;
    private OffsetDateTime purgeAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public String getEditionName() {
        return editionName;
    }

    public void setEditionName(String editionName) {
        this.editionName = editionName;
    }

    public String getDeletedByName() {
        return deletedByName;
    }

    public void setDeletedByName(String deletedByName) {
        this.deletedByName = deletedByName;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public OffsetDateTime getPurgeAt() {
        return purgeAt;
    }

    public void setPurgeAt(OffsetDateTime purgeAt) {
        this.purgeAt = purgeAt;
    }
}
