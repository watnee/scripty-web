package com.scripty.api;

import com.scripty.controller.ActorRestController;
import com.scripty.controller.PersonRestController;
import com.scripty.controller.ProjectRestController;
import com.scripty.dto.Person;
import com.scripty.viewmodel.person.personlist.CharacterViewModel;
import com.scripty.viewmodel.person.personprofile.PersonProfileViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PersonResourceAssembler implements RepresentationModelAssembler<CharacterViewModel, EntityModel<PersonResource>> {

    @Override
    public EntityModel<PersonResource> toModel(CharacterViewModel character) {
        PersonResource resource = new PersonResource();
        resource.setId(character.getId());
        resource.setName(character.getName());
        return EntityModel.of(resource).add(characterLinks(character.getId(), null, null));
    }

    public EntityModel<PersonResource> toModel(CharacterViewModel character, Integer projectId) {
        PersonResource resource = new PersonResource();
        resource.setId(character.getId());
        resource.setName(character.getName());
        resource.setProjectId(projectId);
        return EntityModel.of(resource).add(characterLinks(character.getId(), projectId, null));
    }

    public EntityModel<PersonResource> toModel(PersonProfileViewModel profile) {
        PersonResource resource = new PersonResource();
        resource.setId(profile.getId());
        resource.setName(profile.getName());
        resource.setFullName(profile.getFullName());
        resource.setProjectId(profile.getProjectId());
        resource.setProjectTitle(profile.getProjectTitle());
        if (profile.getActorId() > 0) {
            resource.setActorId(profile.getActorId());
            resource.setActorName(profile.getActorName());
        }
        Integer actorId = profile.getActorId() > 0 ? profile.getActorId() : null;
        return EntityModel.of(resource).add(characterLinks(profile.getId(), profile.getProjectId(), actorId));
    }

    public EntityModel<PersonResource> toModel(Person person) {
        PersonResource resource = new PersonResource();
        resource.setId(person.getId());
        resource.setName(person.getName());
        resource.setFullName(person.getFullName());
        Integer projectId = null;
        Integer actorId = null;
        if (person.getProject() != null) {
            projectId = person.getProject().getId();
            resource.setProjectId(projectId);
            resource.setProjectTitle(person.getProject().getTitle());
        }
        if (person.getActor() != null) {
            actorId = person.getActor().getId();
            resource.setActorId(actorId);
            resource.setActorName(person.getActor().getFirstName() + " " + person.getActor().getLastName());
        }
        return EntityModel.of(resource).add(characterLinks(person.getId(), projectId, actorId));
    }

    public EntityModel<PersonResource> toDeleteModel(Person person) {
        PersonResource resource = new PersonResource();
        resource.setId(person.getId());
        resource.setName(person.getName());
        Integer projectId = person.getProject() != null ? person.getProject().getId() : null;
        if (projectId != null) {
            return EntityModel.of(resource,
                    linkTo(methodOn(PersonRestController.class).list(projectId, null)).withRel(ApiRel.CHARACTERS),
                    linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
        }
        return EntityModel.of(resource,
                linkTo(methodOn(ProjectRestController.class).list(null)).withRel(ApiRel.PROJECTS));
    }

    public CollectionModel<EntityModel<PersonResource>> toCharacterCollection(
            Iterable<CharacterViewModel> characters, Integer projectId) {
        List<EntityModel<PersonResource>> resources = new ArrayList<>();
        for (CharacterViewModel character : characters) {
            resources.add(toModel(character, projectId));
        }
        return CollectionModel.of(resources)
                .add(linkTo(methodOn(PersonRestController.class).list(projectId, null)).withSelfRel())
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
    }

    private org.springframework.hateoas.Link[] characterLinks(int id, Integer projectId, Integer actorId) {
        List<org.springframework.hateoas.Link> links = new ArrayList<>();
        links.add(linkTo(methodOn(PersonRestController.class).show(id, null)).withSelfRel());
        links.add(linkTo(methodOn(PersonRestController.class).update(id, null, null, null)).withRel(ApiRel.UPDATE));
        links.add(linkTo(methodOn(PersonRestController.class).delete(id, null)).withRel(ApiRel.DELETE));
        if (projectId != null) {
            links.add(linkTo(methodOn(PersonRestController.class).list(projectId, null)).withRel(ApiRel.CHARACTERS));
            links.add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
        }
        if (actorId != null) {
            links.add(linkTo(methodOn(ActorRestController.class).show(actorId, null)).withRel(ApiRel.ACTOR));
        }
        return links.toArray(org.springframework.hateoas.Link[]::new);
    }
}
