package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * One team a project may be assigned to, and whether it is assigned now.
 *
 * <p>This is the project side of the web's production page: a checkbox per team
 * with the assigned ones ticked, submitting the chosen ids back through the
 * project's {@code update} affordance ({@code teamIds}). Over the API a client
 * cannot build that list on its own — {@code /api/team} is admin-only, so an
 * ordinary editor has nowhere else to learn team ids — which is the same reason
 * {@link InviteTeamResource} exists for the invite form.
 *
 * <p>The difference from {@code InviteTeamResource}: that one lists only the
 * teams a collaborator may be invited into (the project's own), whereas this
 * lists every team the writer could assign the project to, carrying an
 * {@code assigned} flag so the picker knows which boxes to tick.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.PROJECT_TEAM, collectionRelation = ApiRel.PROJECT_TEAMS)
public class ProjectTeamOptionResource extends RepresentationModel<ProjectTeamOptionResource> {

    private Integer id;
    private String name;
    private boolean assigned;

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

    public boolean isAssigned() {
        return assigned;
    }

    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }
}
