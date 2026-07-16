package com.scripty.api;

import com.scripty.controller.ProjectRestController;
import com.scripty.controller.ProjectVersionRestController;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;
import com.scripty.viewmodel.project.versionhistory.VersionViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Turns a project's {@link VersionHistoryViewModel} into HAL resources for the
 * REST API. Each version links to itself, its project, and the restore/delete
 * affordances; the collection links to itself, the owning project, and the
 * create-version affordance.
 */
@Component
public class ProjectVersionResourceAssembler {

    public EntityModel<ProjectVersionResource> toModel(VersionViewModel version, int projectId, Integer editionId) {
        return EntityModel.of(toResource(version), versionLinks(version.getId(), projectId, editionId));
    }

    public CollectionModel<EntityModel<ProjectVersionResource>> toCollection(
            VersionHistoryViewModel viewModel, Integer editionId) {
        int projectId = viewModel.getProjectId();
        List<EntityModel<ProjectVersionResource>> resources = new ArrayList<>();
        if (viewModel.getVersions() != null) {
            for (VersionViewModel version : viewModel.getVersions()) {
                resources.add(toModel(version, projectId, editionId));
            }
        }
        return CollectionModel.of(resources)
                .add(linkTo(methodOn(ProjectVersionRestController.class).list(projectId, editionId, null))
                        .withSelfRel())
                .add(linkTo(methodOn(ProjectVersionRestController.class).create(projectId, editionId, null, null))
                        .withRel(ApiRel.CREATE))
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT));
    }

    private ProjectVersionResource toResource(VersionViewModel version) {
        ProjectVersionResource resource = new ProjectVersionResource();
        resource.setId(version.getId());
        resource.setLabel(version.getLabel());
        resource.setCreatedAt(ApiDates.toOffset(version.getCreatedAt()));
        resource.setAutoSave(version.isAutoSave());
        resource.setSceneCount(version.getSceneCount());
        resource.setBlockCount(version.getBlockCount());
        resource.setCharacterCount(version.getCharacterCount());
        resource.setChangeSummary(version.getChangeSummary());
        return resource;
    }

    private Link[] versionLinks(int id, int projectId, Integer editionId) {
        return new Link[]{
                linkTo(methodOn(ProjectVersionRestController.class).show(id, projectId, editionId, null))
                        .withSelfRel(),
                linkTo(methodOn(ProjectVersionRestController.class).list(projectId, editionId, null))
                        .withRel(ApiRel.VERSIONS),
                linkTo(methodOn(ProjectVersionRestController.class).restore(id, projectId, editionId, null))
                        .withRel(ApiRel.RESTORE),
                linkTo(methodOn(ProjectVersionRestController.class).delete(id, projectId, editionId, null))
                        .withRel(ApiRel.DELETE),
                linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT)
        };
    }
}
