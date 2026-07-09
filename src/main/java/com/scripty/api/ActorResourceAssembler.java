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

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ActorResourceAssembler implements RepresentationModelAssembler<ActorViewModel, EntityModel<ActorResource>> {

    @Override
    public EntityModel<ActorResource> toModel(ActorViewModel actor) {
        return EntityModel.of(toResource(actor)).add(actorLinks(actor.getId(), null, actor.isHasHeadshot()));
    }

    public EntityModel<ActorResource> toModel(ActorViewModel actor, Integer projectId) {
        return EntityModel.of(toResource(actor)).add(actorLinks(actor.getId(), projectId, actor.isHasHeadshot()));
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
                linkTo(methodOn(ActorRestController.class).list(null)).withRel(ApiRel.ACTORS));
    }

    public CollectionModel<EntityModel<ActorResource>> toActorCollection(
            Iterable<ActorViewModel> actors, Integer projectId) {
        List<EntityModel<ActorResource>> resources = new ArrayList<>();
        for (ActorViewModel actor : actors) {
            resources.add(toModel(actor, projectId));
        }
        CollectionModel<EntityModel<ActorResource>> collection = CollectionModel.of(resources)
                .add(linkTo(methodOn(ActorRestController.class).list(projectId)).withSelfRel());
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
                .add(linkTo(methodOn(ActorRestController.class).list(null)).withSelfRel());
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
        links.add(linkTo(methodOn(ActorRestController.class).show(id)).withSelfRel());
        links.add(linkTo(methodOn(ActorRestController.class).list(projectId)).withRel(ApiRel.ACTORS));
        links.add(linkTo(methodOn(ActorRestController.class).update(id, null, null)).withRel(ApiRel.UPDATE));
        links.add(linkTo(methodOn(ActorRestController.class).delete(id)).withRel(ApiRel.DELETE));
        if (Boolean.TRUE.equals(hasHeadshot)) {
            links.add(linkTo(methodOn(ActorController.class).headshot(id)).withRel(ApiRel.HEADSHOT));
        }
        if (projectId != null) {
            links.add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
        }
        return links.toArray(org.springframework.hateoas.Link[]::new);
    }
}
