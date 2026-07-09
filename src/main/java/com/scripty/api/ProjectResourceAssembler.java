package com.scripty.api;

import com.scripty.controller.ActorRestController;
import com.scripty.controller.BlockRestController;
import com.scripty.controller.PersonRestController;
import com.scripty.controller.ProjectController;
import com.scripty.controller.ProjectRestController;
import com.scripty.dto.Project;
import com.scripty.viewmodel.project.projectlist.ProjectTeamViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
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
public class ProjectResourceAssembler implements RepresentationModelAssembler<ProjectViewModel, EntityModel<ProjectResource>> {

    @Override
    public EntityModel<ProjectResource> toModel(ProjectViewModel project) {
        return EntityModel.of(toResource(project)).add(projectLinks(project.getId()));
    }

    public EntityModel<ProjectResource> toModel(ProjectProfileViewModel profile) {
        ProjectResource resource = new ProjectResource();
        resource.setId(profile.getId());
        resource.setTitle(profile.getTitle());
        resource.setScreenplayTitle(profile.getScreenplayTitle());
        resource.setWriters(profile.getWriters());
        resource.setContactInfo(profile.getContactInfo());
        resource.setLastEdited(profile.getLastEdited());
        resource.setTeams(profile.getTeams());
        return EntityModel.of(resource).add(projectLinks(profile.getId()));
    }

    public EntityModel<ProjectResource> toModel(Project project) {
        ProjectResource resource = new ProjectResource();
        resource.setId(project.getId());
        resource.setTitle(project.getTitle());
        resource.setScreenplayTitle(project.getScreenplayTitle());
        resource.setWriters(project.getWriters());
        resource.setContactInfo(project.getContactInfo());
        resource.setLastEdited(project.getLastEdited());
        resource.setTeams(project.getTeamNames());
        return EntityModel.of(resource).add(projectLinks(project.getId()));
    }

    public EntityModel<ProjectResource> toDeleteModel(Project project) {
        ProjectResource resource = new ProjectResource();
        resource.setId(project.getId());
        resource.setTitle(project.getTitle());
        return EntityModel.of(resource,
                linkTo(methodOn(ProjectRestController.class).list()).withRel(ApiRel.PROJECTS));
    }

    public CollectionModel<EntityModel<ProjectResource>> toProjectCollection(Iterable<ProjectViewModel> projects) {
        List<EntityModel<ProjectResource>> resources = new ArrayList<>();
        for (ProjectViewModel project : projects) {
            resources.add(toModel(project));
        }
        return CollectionModel.of(resources)
                .add(linkTo(methodOn(ProjectRestController.class).list()).withSelfRel());
    }

    private ProjectResource toResource(ProjectViewModel project) {
        ProjectResource resource = new ProjectResource();
        resource.setId(project.getId());
        resource.setTitle(project.getTitle());
        resource.setLastEdited(project.getLastEdited());
        if (project.getTeams() != null) {
            resource.setTeams(project.getTeams().stream()
                    .map(ProjectTeamViewModel::getName)
                    .collect(Collectors.toList()));
        }
        return resource;
    }

    private org.springframework.hateoas.Link[] projectLinks(int id) {
        return new org.springframework.hateoas.Link[]{
                linkTo(methodOn(ProjectRestController.class).show(id)).withSelfRel(),
                linkTo(methodOn(ProjectRestController.class).list()).withRel(ApiRel.PROJECTS),
                linkTo(methodOn(ProjectRestController.class).update(id, null, null)).withRel(ApiRel.UPDATE),
                linkTo(methodOn(ProjectRestController.class).delete(id)).withRel(ApiRel.DELETE),
                linkTo(methodOn(BlockRestController.class).list(id)).withRel(ApiRel.BLOCKS),
                linkTo(methodOn(PersonRestController.class).list(id)).withRel(ApiRel.CHARACTERS),
                linkTo(methodOn(ActorRestController.class).list(id)).withRel(ApiRel.ACTORS),
                linkTo(methodOn(ProjectController.class).syncStatus(id, null)).withRel(ApiRel.SYNC_STATUS),
                linkTo(methodOn(ProjectController.class).undoRedoStatus(id)).withRel(ApiRel.UNDO_REDO_STATUS),
                linkTo(methodOn(ProjectController.class).exportScript(id)).withRel(ApiRel.EXPORT)
        };
    }
}
