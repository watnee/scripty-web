package com.scripty.api;

import com.scripty.controller.ProjectRestController;
import com.scripty.controller.SongVersionRestController;
import com.scripty.controller.TextDocumentRestController;
import com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel;
import com.scripty.viewmodel.song.versionhistory.SongVersionViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Turns a song's {@link SongVersionHistoryViewModel} into HAL resources for the
 * REST API, mirroring {@link ProjectVersionResourceAssembler}. Each version
 * links to itself, its song, its project, and the restore/delete affordances;
 * the collection links to itself, the owning song and project, and the
 * create-version affordance.
 */
@Component
public class SongVersionResourceAssembler {

    public EntityModel<SongVersionResource> toModel(SongVersionViewModel version, int documentId, int projectId) {
        return EntityModel.of(toResource(version), versionLinks(version.getId(), documentId, projectId));
    }

    public CollectionModel<EntityModel<SongVersionResource>> toCollection(SongVersionHistoryViewModel viewModel) {
        int documentId = viewModel.getDocumentId();
        int projectId = viewModel.getProjectId();
        List<EntityModel<SongVersionResource>> resources = new ArrayList<>();
        if (viewModel.getVersions() != null) {
            for (SongVersionViewModel version : viewModel.getVersions()) {
                resources.add(toModel(version, documentId, projectId));
            }
        }
        return CollectionModel.of(resources)
                .add(linkTo(methodOn(SongVersionRestController.class).list(documentId, null))
                        .withSelfRel())
                .add(linkTo(methodOn(SongVersionRestController.class).create(documentId, null, null))
                        .withRel(ApiRel.CREATE))
                .add(linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                        .withRel(ApiRel.DOCUMENT))
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT));
    }

    private SongVersionResource toResource(SongVersionViewModel version) {
        SongVersionResource resource = new SongVersionResource();
        resource.setId(version.getId());
        resource.setLabel(version.getLabel());
        resource.setTitle(version.getTitle());
        resource.setCreatedAt(ApiDates.toOffset(version.getCreatedAt()));
        resource.setAutoSave(version.isAutoSave());
        resource.setLineCount(version.getLineCount());
        resource.setChangeSummary(version.getChangeSummary());
        return resource;
    }

    private Link[] versionLinks(int id, int documentId, int projectId) {
        return new Link[]{
                linkTo(methodOn(SongVersionRestController.class).show(id, documentId, null))
                        .withSelfRel(),
                linkTo(methodOn(SongVersionRestController.class).list(documentId, null))
                        .withRel(ApiRel.VERSIONS),
                linkTo(methodOn(SongVersionRestController.class).restore(id, documentId, null))
                        .withRel(ApiRel.RESTORE),
                linkTo(methodOn(SongVersionRestController.class).delete(id, documentId, null))
                        .withRel(ApiRel.DELETE),
                linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                        .withRel(ApiRel.DOCUMENT),
                linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT)
        };
    }
}
