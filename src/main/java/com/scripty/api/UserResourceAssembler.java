package com.scripty.api;

import com.scripty.controller.UserRestController;
import com.scripty.dto.User;
import com.scripty.viewmodel.user.userlist.UserViewModel;
import com.scripty.viewmodel.user.userprofile.UserProfileViewModel;
import com.scripty.viewmodel.user.userprofile.UserProjectAccessViewModel;
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
        resource.setCastingDirector(profile.isCastingDirector());
        resource.setViewCasting(profile.isViewCasting());
        resource.setDeveloper(profile.isDeveloper());
        resource.setEnabled(profile.isEnabled());
        resource.setProjectAccess(toProjectAccess(profile.getProjectAccess()));
        return EntityModel.of(resource).add(userLinks(profile.getId()));
    }

    /// Maps the computed per-project access rows onto the resource. Only the
    /// profile path carries these — the list (`toResource`) and the
    /// create/update (`User`) paths leave `projectAccess` null, so NON_NULL
    /// omits it there, matching the web where only the profile page shows it.
    private List<UserProjectAccessResource> toProjectAccess(
            List<UserProjectAccessViewModel> access) {
        if (access == null) {
            return null;
        }
        List<UserProjectAccessResource> resources = new ArrayList<>();
        for (UserProjectAccessViewModel vm : access) {
            UserProjectAccessResource resource = new UserProjectAccessResource();
            resource.setProjectId(vm.getProjectId());
            resource.setProjectName(vm.getProjectName());
            resource.setCanEdit(vm.isCanEdit());
            resource.setPermissionLabel(vm.getPermissionLabel());
            resource.setAccessReason(vm.getAccessReason());
            resources.add(resource);
        }
        return resources;
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
        resource.setCastingDirector(user.isCastingDirector());
        resource.setViewCasting(user.isViewCasting());
        resource.setDeveloper(user.isDeveloper());
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
        resource.setCastingDirector(user.isCastingDirector());
        resource.setViewCasting(user.isViewCasting());
        resource.setDeveloper(user.isDeveloper());
        resource.setEnabled(user.isEnabled());
        return resource;
    }

    private org.springframework.hateoas.Link[] userLinks(int id) {
        return new org.springframework.hateoas.Link[]{
                linkTo(methodOn(UserRestController.class).show(id)).withSelfRel(),
                linkTo(methodOn(UserRestController.class).list()).withRel(ApiRel.USERS),
                linkTo(methodOn(UserRestController.class).update(id, null, null)).withRel(ApiRel.UPDATE),
                linkTo(methodOn(UserRestController.class).delete(id, null)).withRel(ApiRel.DELETE)
        };
    }
}
