package com.scripty.api;

import com.scripty.controller.ProjectRestController;
import com.scripty.controller.ProjectVersionRestController;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;
import com.scripty.viewmodel.project.versionhistory.VersionViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.afford;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Turns a project's {@link VersionHistoryViewModel} into HAL resources for the
 * REST API. Each version links to itself, its project, and the restore/delete
 * affordances; the collection links to itself, the owning project, and the
 * create-version affordance.
 *
 * <p>Reading version history only needs project access, but restore, delete and
 * create require edit permission — see {@link ProjectVersionRestController}. The
 * mutation links and affordances are therefore gated on {@code canEdit} so a
 * read-only viewer is not advertised actions that would answer 403, matching
 * {@link SongVersionResourceAssembler}.
 */
@Component
public class ProjectVersionResourceAssembler {

    @Autowired
    ProjectAccessSupport projectAccess;

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
        Link self = linkTo(methodOn(ProjectVersionRestController.class).list(projectId, editionId, null))
                .withSelfRel();
        if (canEdit(projectId)) {
            self = self.andAffordance(afford(methodOn(ProjectVersionRestController.class)
                    .create(projectId, editionId, null, null)));
        }
        CollectionModel<EntityModel<ProjectVersionResource>> collection = CollectionModel.of(resources)
                .add(self)
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT));
        if (canEdit(projectId)) {
            collection.add(linkTo(methodOn(ProjectVersionRestController.class).create(projectId, editionId, null, null))
                    .withRel(ApiRel.CREATE));
        }
        return collection;
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
        boolean canEdit = canEdit(projectId);
        List<Link> links = new ArrayList<>();
        Link self = linkTo(methodOn(ProjectVersionRestController.class).show(id, projectId, editionId, null))
                .withSelfRel();
        if (canEdit) {
            self = self
                    .andAffordance(afford(methodOn(ProjectVersionRestController.class)
                            .restore(id, projectId, editionId, null)))
                    .andAffordance(afford(methodOn(ProjectVersionRestController.class)
                            .delete(id, projectId, editionId, null)));
        }
        links.add(self);
        links.add(linkTo(methodOn(ProjectVersionRestController.class).list(projectId, editionId, null))
                .withRel(ApiRel.VERSIONS));
        if (canEdit) {
            links.add(linkTo(methodOn(ProjectVersionRestController.class).restore(id, projectId, editionId, null))
                    .withRel(ApiRel.RESTORE));
            links.add(linkTo(methodOn(ProjectVersionRestController.class).delete(id, projectId, editionId, null))
                    .withRel(ApiRel.DELETE));
        }
        links.add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                .withRel(ApiRel.PROJECT));
        return links.toArray(Link[]::new);
    }

    private boolean canEdit(Integer projectId) {
        return projectAccess.canEditScriptForCurrentUser(projectId);
    }
}
