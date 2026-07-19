package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of one entry in a project's activity log.
 *
 * <p>The summary is phrased by the server when the event is recorded, so a
 * client renders it rather than reconstructing a sentence from the action type.
 * {@code actionType} is still exposed so a client can group or icon entries
 * without parsing prose.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.ACTIVITY_ENTRY, collectionRelation = ApiRel.ACTIVITY)
public class ProjectActivityResource extends RepresentationModel<ProjectActivityResource> {

    private Integer id;
    private String actorDisplayName;
    private String actionType;
    private String summary;
    private OffsetDateTime createdAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getActorDisplayName() {
        return actorDisplayName;
    }

    public void setActorDisplayName(String actorDisplayName) {
        this.actorDisplayName = actorDisplayName;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
