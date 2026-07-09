package com.scripty.api;

import com.scripty.controller.UserRestController;
import com.scripty.dto.User;
import com.scripty.viewmodel.user.userlist.UserViewModel;
import com.scripty.viewmodel.user.userprofile.UserProfileViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class UserResourceAssembler implements RepresentationModelAssembler<UserViewModel, EntityModel<UserResource>> {

    @Override
    public EntityModel<UserResource> toModel(UserViewModel user) {
        return EntityModel.of(toResource(user)).add(userLinks(user.getId()));
    }

    public EntityModel<UserResource> toModel(UserProfileViewModel profile) {
        UserResource resource = new UserResource();
        resource.setId(profile.getId());
        resource.setUsername(profile.getUsername());
        resource.setFirstName(profile.getFirstName());
        resource.setLastName(profile.getLastName());
        resource.setTeam(profile.getTeam());
        resource.setAdmin(profile.isAdmin());
        resource.setDirector(profile.isDirector());
        resource.setProducer(profile.isProducer());
        resource.setWriter(profile.isWriter());
        resource.setActor(profile.isActor());
        resource.setCrew(profile.isCrew());
        resource.setDirectorOfPhotography(profile.isDirectorOfPhotography());
        resource.setEnabled(profile.isEnabled());
        return EntityModel.of(resource).add(userLinks(profile.getId()));
    }

    public EntityModel<UserResource> toModel(User user) {
        UserResource resource = new UserResource();
        resource.setId(user.getId());
        resource.setUsername(user.getUsername());
        resource.setFirstName(user.getFirstName());
        resource.setLastName(user.getLastName());
        resource.setTeam(user.getTeam());
        resource.setAdmin(user.isAdmin());
        resource.setDirector(user.isDirector());
        resource.setProducer(user.isProducer());
        resource.setWriter(user.isWriter());
        resource.setActor(user.isActor());
        resource.setCrew(user.isCrew());
        resource.setDirectorOfPhotography(user.isDirectorOfPhotography());
        return EntityModel.of(resource).add(userLinks(user.getId()));
    }

    public EntityModel<UserResource> toDeleteModel(User user) {
        UserResource resource = new UserResource();
        resource.setId(user.getId());
        resource.setUsername(user.getUsername());
        return EntityModel.of(resource,
                linkTo(methodOn(UserRestController.class).list()).withRel(ApiRel.USERS));
    }

    public CollectionModel<EntityModel<UserResource>> toUserCollection(Iterable<UserViewModel> users) {
        List<EntityModel<UserResource>> resources = new ArrayList<>();
        for (UserViewModel user : users) {
            resources.add(toModel(user));
        }
        return CollectionModel.of(resources)
                .add(linkTo(methodOn(UserRestController.class).list()).withSelfRel());
    }

    private UserResource toResource(UserViewModel user) {
        UserResource resource = new UserResource();
        resource.setId(user.getId());
        resource.setUsername(user.getUsername());
        resource.setFirstName(user.getFirstName());
        resource.setLastName(user.getLastName());
        resource.setTeam(user.getTeam());
        resource.setAdmin(user.isAdmin());
        resource.setDirector(user.isDirector());
        resource.setProducer(user.isProducer());
        resource.setWriter(user.isWriter());
        resource.setActor(user.isActor());
        resource.setCrew(user.isCrew());
        resource.setDirectorOfPhotography(user.isDirectorOfPhotography());
        resource.setEnabled(user.isEnabled());
        return resource;
    }

    private org.springframework.hateoas.Link[] userLinks(int id) {
        return new org.springframework.hateoas.Link[]{
                linkTo(methodOn(UserRestController.class).show(id)).withSelfRel(),
                linkTo(methodOn(UserRestController.class).list()).withRel(ApiRel.USERS),
                linkTo(methodOn(UserRestController.class).update(id, null, null)).withRel(ApiRel.UPDATE),
                linkTo(methodOn(UserRestController.class).delete(id)).withRel(ApiRel.DELETE)
        };
    }
}
