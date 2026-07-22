package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * One team a collaborator can be invited into.
 *
 * <p>Inviting an editor requires a team, and the only valid choices are the
 * teams the project is already assigned to — the web's invite form renders
 * exactly this list as a required select. Without it over the API a client can
 * only guess at team ids, and {@code /api/team} is admin-only, so an ordinary
 * editor has nowhere else to learn them.
 *
 * <p>Deliberately just an id and a name. This is a picker's worth of a team,
 * not the team resource: it says what may be chosen here, and says nothing
 * about who is on it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.INVITE_TEAM, collectionRelation = ApiRel.INVITE_TEAMS)
public class InviteTeamResource extends RepresentationModel<InviteTeamResource> {

    private Integer id;
    private String name;

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
}
