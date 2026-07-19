package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of a screenplay sitting in the trash.
 *
 * <p>Deliberately thin — a title and when it was deleted. A trashed project is
 * something you identify and either restore or destroy; its blocks, characters
 * and documents come back with it on restore and are nobody's business until
 * then.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.TRASHED_PROJECT, collectionRelation = ApiRel.TRASHED_PROJECTS)
public class TrashedProjectResource extends RepresentationModel<TrashedProjectResource> {

    private Integer id;
    private String title;
    private OffsetDateTime deletedAt;

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

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
