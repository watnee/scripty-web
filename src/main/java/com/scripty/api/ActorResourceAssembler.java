package com.scripty.api;

import com.scripty.controller.ActorController;
import com.scripty.controller.ActorRestController;
import com.scripty.controller.ProjectRestController;
import com.scripty.dto.Actor;
import com.scripty.dto.Project;
import com.scripty.viewmodel.actor.actorlist.ActorViewModel;
import com.scripty.viewmodel.actor.actorprofile.ActorProfileViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.afford;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ActorResourceAssembler implements RepresentationModelAssembler<ActorViewModel, EntityModel<ActorResource>> {

    @Override
    public EntityModel<ActorResource> toModel(ActorViewModel actor) {
        return EntityModel.of(toResource(actor)).add(actorLinks(actor.getId(), null, actor.isHasHeadshot()));
    }

    public EntityModel<ActorResource> toModel(ActorViewModel actor, Integer projectId) {
        ActorResource resource = toResource(actor);
        // Auditions only mean something within a project, so the ids ride along
        // only on a project-scoped actor. An empty list is still sent — it says
        // "auditions for no one", which a client must be able to tell apart from
        // "auditions not in scope" (null, omitted).
        if (projectId != null) {
            resource.setAuditionCharacterIds(new ArrayList<>(actor.getAuditionCharacterIds()));
        }
        return EntityModel.of(resource).add(actorLinks(actor.getId(), projectId, actor.isHasHeadshot()));
    }

    public EntityModel<ActorResource> toModel(ActorProfileViewModel profile) {
        ActorResource resource = new ActorResource();
        resource.setId(profile.getId());
        resource.setFirst(profile.getFirst());
        resource.setLast(profile.getLast());
        resource.setPhone(profile.getPhone());
        resource.setEmail(profile.getEmail());
        resource.setHasHeadshot(profile.isHasHeadshot());
        if (profile.getProjects() != null) {
            resource.setProjectIds(profile.getProjects().stream()
                    .map(p -> p.getId())
                    .collect(Collectors.toList()));
        }
        return EntityModel.of(resource).add(actorLinks(profile.getId(), null, profile.isHasHeadshot()));
    }

    public EntityModel<ActorResource> toModel(Actor actor) {
        ActorResource resource = new ActorResource();
        resource.setId(actor.getId());
        resource.setFirst(actor.getFirstName());
        resource.setLast(actor.getLastName());
        resource.setPhone(actor.getPhone());
        resource.setEmail(actor.getEmail());
        resource.setHasHeadshot(actor.getHeadshotPath() != null && !actor.getHeadshotPath().isBlank());
        if (actor.getProjects() != null) {
            resource.setProjectIds(actor.getProjects().stream()
                    .map(Project::getId)
                    .collect(Collectors.toList()));
        }
        return EntityModel.of(resource).add(actorLinks(actor.getId(), null, resource.getHasHeadshot()));
    }

    public EntityModel<ActorResource> toDeleteModel(Actor actor) {
        ActorResource resource = new ActorResource();
        resource.setId(actor.getId());
        resource.setFirst(actor.getFirstName());
        resource.setLast(actor.getLastName());
        return EntityModel.of(resource,
                linkTo(methodOn(ActorRestController.class).list(null, null)).withRel(ApiRel.ACTORS));
    }

    public CollectionModel<EntityModel<ActorResource>> toActorCollection(
            Iterable<ActorViewModel> actors, Integer projectId) {
        List<EntityModel<ActorResource>> resources = new ArrayList<>();
        for (ActorViewModel actor : actors) {
            resources.add(toModel(actor, projectId));
        }
        CollectionModel<EntityModel<ActorResource>> collection = CollectionModel.of(resources)
                .add(linkTo(methodOn(ActorRestController.class).list(projectId, null)).withSelfRel());
        if (projectId != null) {
            collection.add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
        }
        return collection;
    }

    public CollectionModel<EntityModel<ActorResource>> toActorCollectionFromEntities(Iterable<Actor> actors) {
        List<EntityModel<ActorResource>> resources = new ArrayList<>();
        for (Actor actor : actors) {
            resources.add(toModel(actor));
        }
        return CollectionModel.of(resources)
                .add(linkTo(methodOn(ActorRestController.class).list(null, null)).withSelfRel());
    }

    private ActorResource toResource(ActorViewModel actor) {
        ActorResource resource = new ActorResource();
        resource.setId(actor.getId());
        resource.setFirst(actor.getFirst());
        resource.setLast(actor.getLast());
        resource.setPhone(actor.getPhone());
        resource.setEmail(actor.getEmail());
        resource.setHasHeadshot(actor.isHasHeadshot());
        return resource;
    }

    private org.springframework.hateoas.Link[] actorLinks(int id, Integer projectId, Boolean hasHeadshot) {
        List<org.springframework.hateoas.Link> links = new ArrayList<>();
        links.add(linkTo(methodOn(ActorRestController.class).show(id, null)).withSelfRel()
                .andAffordance(afford(methodOn(ActorRestController.class).update(id, null, null, null)))
                .andAffordance(afford(methodOn(ActorRestController.class).delete(id, null))));
        links.add(linkTo(methodOn(ActorRestController.class).list(projectId, null)).withRel(ApiRel.ACTORS));
        links.add(linkTo(methodOn(ActorRestController.class).update(id, null, null, null)).withRel(ApiRel.UPDATE));
        links.add(linkTo(methodOn(ActorRestController.class).delete(id, null)).withRel(ApiRel.DELETE));
        if (Boolean.TRUE.equals(hasHeadshot)) {
            links.add(linkTo(methodOn(ActorController.class).headshot(id, null)).withRel(ApiRel.HEADSHOT));
        }
        if (projectId != null) {
            // Setting auditions is a per-project action, so it is offered only on
            // a project-scoped actor.
            links.add(linkTo(methodOn(ActorRestController.class).setAuditions(id, projectId, null, null))
                    .withRel(ApiRel.SET_AUDITIONS));
            links.add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
        }
        return links.toArray(org.springframework.hateoas.Link[]::new);
    }
}
