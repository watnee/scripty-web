package com.scripty.api;

import com.scripty.controller.ProjectRestController;
import com.scripty.controller.SongBlockRestController;
import com.scripty.controller.SongVersionRestController;
import com.scripty.controller.TextDocumentRestController;
import com.scripty.dto.TextDocument;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.scripty.security.ProjectAccessSupport;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Builds HAL resources for project text documents (songs and notes). Mutation
 * links (update, delete, insert, share, import) appear only when the current
 * user can edit the script — the client gates its UI on their presence, the
 * same rule the block editor uses.
 */
@Component
public class TextDocumentResourceAssembler {

    @Autowired
    ProjectAccessSupport projectAccess;

    public EntityModel<TextDocumentResource> toModel(TextDocumentViewModel document) {
        return EntityModel.of(toResource(document, true))
                .add(documentLinks(document.getId(), document.getProjectId(),
                        document.getDocumentType()));
    }

    public EntityModel<TextDocumentResource> toDeleteModel(Integer projectId) {
        TextDocumentResource resource = new TextDocumentResource();
        resource.setProjectId(projectId);
        return EntityModel.of(resource,
                linkTo(methodOn(TextDocumentRestController.class).list(projectId, null, null))
                        .withRel(ApiRel.DOCUMENTS));
    }

    /** All documents (songs and notes) for one project. */
    public CollectionModel<EntityModel<TextDocumentResource>> toCollection(
            List<TextDocumentViewModel> documents, Integer projectId, String type) {
        List<EntityModel<TextDocumentResource>> resources = new ArrayList<>();
        for (TextDocumentViewModel document : documents) {
            resources.add(toSummaryModel(document));
        }
        CollectionModel<EntityModel<TextDocumentResource>> collection = CollectionModel.of(resources)
                .add(linkTo(methodOn(TextDocumentRestController.class).list(projectId, type, null))
                        .withSelfRel())
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT));
        if (canEdit(projectId)) {
            collection.add(linkTo(methodOn(TextDocumentRestController.class)
                    .importFile(null, null, null, null)).withRel(ApiRel.IMPORT_DOCUMENT));
            collection.add(linkTo(methodOn(TextDocumentRestController.class)
                    .reorder(projectId, null, null)).withRel(ApiRel.REORDER));
        }
        return collection;
    }

    /** Summary form used in the list: preview instead of full content. */
    private EntityModel<TextDocumentResource> toSummaryModel(TextDocumentViewModel document) {
        return EntityModel.of(toResource(document, false))
                .add(documentLinks(document.getId(), document.getProjectId(),
                        document.getDocumentType()));
    }

    private TextDocumentResource toResource(TextDocumentViewModel document, boolean includeContent) {
        TextDocumentResource resource = new TextDocumentResource();
        resource.setId(document.getId());
        resource.setProjectId(document.getProjectId());
        resource.setProjectTitle(document.getProjectTitle());
        resource.setTitle(document.getTitle());
        resource.setDocumentType(document.getDocumentType());
        resource.setDocumentTypeLabel(document.getDocumentTypeLabel());
        resource.setPreview(document.getPreview());
        resource.setSortOrder(document.getSortOrder());
        resource.setCreatedAt(ApiDates.toOffset(document.getCreatedAt()));
        resource.setUpdatedAt(ApiDates.toOffset(document.getUpdatedAt()));
        if (includeContent) {
            resource.setContent(document.getContent());
        }
        return resource;
    }

    private org.springframework.hateoas.Link[] documentLinks(int id, Integer projectId, String type) {
        List<org.springframework.hateoas.Link> links = new ArrayList<>();
        links.add(linkTo(methodOn(TextDocumentRestController.class).show(id, null)).withSelfRel());
        if (TextDocument.TYPE_SONG.equalsIgnoreCase(type)) {
            // Only songs are edited as ordered blocks and versioned; notes are
            // plain content, with no lyrics or history to navigate to.
            links.add(linkTo(methodOn(SongBlockRestController.class).list(id, null))
                    .withRel(ApiRel.SONG_BLOCKS));
            links.add(linkTo(methodOn(SongVersionRestController.class).list(id, null))
                    .withRel(ApiRel.VERSIONS));
        }
        if (projectId != null) {
            links.add(linkTo(methodOn(TextDocumentRestController.class).list(projectId, null, null))
                    .withRel(ApiRel.DOCUMENTS));
            links.add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                    .withRel(ApiRel.PROJECT));
        }
        if (canEdit(projectId)) {
            links.add(linkTo(methodOn(TextDocumentRestController.class).update(id, null, null, null))
                    .withRel(ApiRel.UPDATE));
            links.add(linkTo(methodOn(TextDocumentRestController.class).delete(id, null, null))
                    .withRel(ApiRel.DELETE));
            links.add(linkTo(methodOn(TextDocumentRestController.class).insert(id, null, null))
                    .withRel(ApiRel.INSERT));
            links.add(linkTo(methodOn(TextDocumentRestController.class).duplicate(id, projectId, null))
                    .withRel(ApiRel.DUPLICATE));
            links.add(linkTo(methodOn(TextDocumentRestController.class).changeType(id, null, projectId, null))
                    .withRel(ApiRel.CHANGE_TYPE));
            if (TextDocument.TYPE_SONG.equalsIgnoreCase(type)) {
                links.add(linkTo(methodOn(TextDocumentRestController.class).shareEmail(id, null, null))
                        .withRel(ApiRel.SHARE_EMAIL));
            }
        }
        return links.toArray(org.springframework.hateoas.Link[]::new);
    }

    private boolean canEdit(Integer projectId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (projectId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return projectAccess.canEditScript(projectId, projectAccess.currentUser(authentication));
    }
}
