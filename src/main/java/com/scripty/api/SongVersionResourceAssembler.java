package com.scripty.api;

import com.scripty.controller.ProjectRestController;
import com.scripty.controller.SongBlockRestController;
import com.scripty.controller.SongVersionRestController;
import com.scripty.controller.TextDocumentRestController;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel;
import com.scripty.viewmodel.song.versionhistory.SongVersionViewModel;
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
 * Turns a song's {@link SongVersionHistoryViewModel} into HAL resources for the
 * REST API, mirroring {@link ProjectVersionResourceAssembler}. Each version
 * links to itself, its song, its project and its lyrics; the collection links
 * to itself and the same neighbours.
 *
 * <p>The mutation affordances (restore, delete, create) appear only when the
 * current user can edit the song's project — the client gates its UI on their
 * presence, the same rule {@link TextDocumentResourceAssembler} and
 * {@link SongBlockResourceAssembler} use. The controller enforces the
 * permission regardless; these links only tell an honest client what to offer.
 */
@Component
public class SongVersionResourceAssembler {

    @Autowired
    ProjectAccessSupport projectAccess;

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
        Link self = linkTo(methodOn(SongVersionRestController.class).list(documentId, null))
                .withSelfRel();
        if (canEdit(projectId)) {
            self = self.andAffordance(afford(methodOn(SongVersionRestController.class)
                    .create(documentId, null, null)));
        }
        CollectionModel<EntityModel<SongVersionResource>> collection = CollectionModel.of(resources)
                .add(self)
                .add(linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                        .withRel(ApiRel.SONG))
                .add(linkTo(methodOn(SongBlockRestController.class).list(documentId, null))
                        .withRel(ApiRel.SONG_BLOCKS))
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT));
        if (canEdit(projectId)) {
            collection.add(linkTo(methodOn(SongVersionRestController.class).create(documentId, null, null))
                    .withRel(ApiRel.CREATE));
        }
        return collection;
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
        List<Link> links = new ArrayList<>();
        Link self = linkTo(methodOn(SongVersionRestController.class).show(id, documentId, null))
                .withSelfRel();
        if (canEdit(projectId)) {
            self = self
                    .andAffordance(afford(methodOn(SongVersionRestController.class).restore(id, documentId, null)))
                    .andAffordance(afford(methodOn(SongVersionRestController.class).delete(id, documentId, null)));
        }
        links.add(self);
        links.add(linkTo(methodOn(SongVersionRestController.class).list(documentId, null))
                .withRel(ApiRel.VERSIONS));
        if (canEdit(projectId)) {
            links.add(linkTo(methodOn(SongVersionRestController.class).restore(id, documentId, null))
                    .withRel(ApiRel.RESTORE));
            links.add(linkTo(methodOn(SongVersionRestController.class).delete(id, documentId, null))
                    .withRel(ApiRel.DELETE));
        }
        links.add(linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                .withRel(ApiRel.SONG));
        links.add(linkTo(methodOn(SongBlockRestController.class).list(documentId, null))
                .withRel(ApiRel.SONG_BLOCKS));
        links.add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                .withRel(ApiRel.PROJECT));
        return links.toArray(Link[]::new);
    }

    private boolean canEdit(Integer projectId) {
        return projectAccess.canEditScriptForCurrentUser(projectId);
    }
}
