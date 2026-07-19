package com.scripty.api;

import com.scripty.controller.TeamRestController;
import com.scripty.dto.Team;
import java.util.ArrayList;
import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class TeamResourceAssembler implements RepresentationModelAssembler<Team, EntityModel<TeamResource>> {

    @Override
    public EntityModel<TeamResource> toModel(Team team) {
        return EntityModel.of(toResource(team)).add(teamLinks(team.getId()));
    }

    public EntityModel<TeamResource> toDeleteModel(Team team) {
        TeamResource resource = new TeamResource();
        resource.setId(team.getId());
        resource.setName(team.getName());
        return EntityModel.of(resource,
                linkTo(methodOn(TeamRestController.class).list()).withRel(ApiRel.TEAMS));
    }

    public CollectionModel<EntityModel<TeamResource>> toTeamCollection(Iterable<Team> teams) {
        List<EntityModel<TeamResource>> resources = new ArrayList<>();
        for (Team team : teams) {
            resources.add(toModel(team));
        }
        return CollectionModel.of(resources)
                .add(linkTo(methodOn(TeamRestController.class).list()).withSelfRel());
    }

    private TeamResource toResource(Team team) {
        TeamResource resource = new TeamResource();
        resource.setId(team.getId());
        resource.setName(team.getName());
        return resource;
    }

    private org.springframework.hateoas.Link[] teamLinks(int id) {
        return new org.springframework.hateoas.Link[]{
                linkTo(methodOn(TeamRestController.class).show(id)).withSelfRel(),
                linkTo(methodOn(TeamRestController.class).list()).withRel(ApiRel.TEAMS),
                linkTo(methodOn(TeamRestController.class).update(id, null, null)).withRel(ApiRel.UPDATE),
                linkTo(methodOn(TeamRestController.class).assignProductions(id, null))
                        .withRel(ApiRel.ASSIGN_PRODUCTIONS),
                linkTo(methodOn(TeamRestController.class).delete(id)).withRel(ApiRel.DELETE)
        };
    }
}
