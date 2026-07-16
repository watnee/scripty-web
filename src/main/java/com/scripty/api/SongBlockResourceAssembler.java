package com.scripty.api;

import com.scripty.controller.SongBlockRestController;
import com.scripty.controller.TextDocumentRestController;
import com.scripty.dto.SongBlock;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.viewmodel.songblock.SongBlockViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Builds HAL resources for a song's lyric lines. Mutation links appear only
 * when the current user may edit the song — the client gates its UI on their
 * presence, the same rule {@link TextDocumentResourceAssembler} uses.
 *
 * <p>Note the access rule differs from {@link BlockResourceAssembler}: songs
 * follow the song editor, where any project member may edit, whereas screenplay
 * blocks require script write access.
 */
@Component
public class SongBlockResourceAssembler {

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    SongBlockService songBlockService;

    public EntityModel<SongBlockResource> toModel(SongBlockViewModel block) {
        return EntityModel.of(toResource(block))
                .add(blockLinks(block.getId(), block.getDocumentId()));
    }

    /**
     * Model for a block the service returned from a mutation. The document id is
     * supplied by the caller, which already resolved it — {@link SongBlock}
     * holds its document lazily.
     */
    public EntityModel<SongBlockResource> toModel(SongBlock block, Integer documentId) {
        return EntityModel.of(toResource(block, documentId))
                .add(blockLinks(block.getId(), documentId));
    }

    /** Points a deleted block's client back at the song it was removed from. */
    public EntityModel<SongBlockResource> toDeleteModel(Integer documentId) {
        SongBlockResource resource = new SongBlockResource();
        resource.setDocumentId(documentId);
        return EntityModel.of(resource, documentLinks(documentId));
    }

    /** All lyric lines of one song, in order. */
    public CollectionModel<EntityModel<SongBlockResource>> toCollection(
            List<SongBlockViewModel> blocks, Integer documentId) {
        List<EntityModel<SongBlockResource>> resources = new ArrayList<>();
        for (SongBlockViewModel block : blocks) {
            resources.add(toModel(block));
        }
        CollectionModel<EntityModel<SongBlockResource>> collection = CollectionModel.of(resources)
                .add(linkTo(methodOn(SongBlockRestController.class).list(documentId, null)).withSelfRel())
                .add(linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                        .withRel(ApiRel.DOCUMENT));
        if (canEditDocument(documentId)) {
            collection.add(linkTo(methodOn(SongBlockRestController.class).append(documentId, null))
                    .withRel(ApiRel.APPEND));
            collection.add(linkTo(methodOn(SongBlockRestController.class).undo(documentId, null))
                    .withRel(ApiRel.UNDO));
            collection.add(linkTo(methodOn(SongBlockRestController.class).redo(documentId, null))
                    .withRel(ApiRel.REDO));
            collection.add(linkTo(methodOn(SongBlockRestController.class).undoRedoStatus(documentId, null))
                    .withRel(ApiRel.UNDO_REDO_STATUS));
        }
        return collection;
    }

    private SongBlockResource toResource(SongBlockViewModel block) {
        SongBlockResource resource = new SongBlockResource();
        resource.setId(block.getId());
        resource.setDocumentId(block.getDocumentId());
        resource.setOrder(block.getOrder());
        resource.setContent(block.getContent());
        resource.setHighlight(block.getHighlight());
        return resource;
    }

    private SongBlockResource toResource(SongBlock block, Integer documentId) {
        SongBlockResource resource = new SongBlockResource();
        resource.setId(block.getId());
        resource.setDocumentId(documentId);
        resource.setOrder(block.getOrder());
        resource.setContent(block.getContent());
        resource.setHighlight(block.getHighlight());
        return resource;
    }

    private Link[] blockLinks(Integer id, Integer documentId) {
        List<Link> links = new ArrayList<>();
        links.add(linkTo(methodOn(SongBlockRestController.class).show(id, null)).withSelfRel());
        if (canEditDocument(documentId)) {
            links.add(linkTo(methodOn(SongBlockRestController.class).update(id, null, null))
                    .withRel(ApiRel.UPDATE));
            links.add(linkTo(methodOn(SongBlockRestController.class).delete(id, null))
                    .withRel(ApiRel.DELETE));
            links.add(linkTo(methodOn(SongBlockRestController.class).createBelow(id, null, null))
                    .withRel(ApiRel.CREATE_BELOW));
            links.add(linkTo(methodOn(SongBlockRestController.class).setHighlight(id, null, null))
                    .withRel(ApiRel.SET_HIGHLIGHT));
            links.add(linkTo(methodOn(SongBlockRestController.class).move(id, null, null))
                    .withRel(ApiRel.MOVE));
        }
        if (documentId != null) {
            links.addAll(List.of(documentLinks(documentId)));
        }
        return links.toArray(Link[]::new);
    }

    private Link[] documentLinks(Integer documentId) {
        if (documentId == null) {
            return new Link[0];
        }
        return new Link[]{
            linkTo(methodOn(SongBlockRestController.class).list(documentId, null))
                    .withRel(ApiRel.SONG_BLOCKS),
            linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                    .withRel(ApiRel.DOCUMENT)
        };
    }

    /**
     * Songs are editable by any project member, so this mirrors the song
     * editor's own check rather than the screenplay's write-access one.
     */
    private boolean canEditDocument(Integer documentId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (documentId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null
                && projectAccess.canAccessProject(projectId, projectAccess.currentUser(authentication));
    }
}
