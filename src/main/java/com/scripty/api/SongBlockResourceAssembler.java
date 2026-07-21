package com.scripty.api;

import com.scripty.controller.ProjectRestController;
import com.scripty.controller.SongBlockRestController;
import com.scripty.controller.SongBlockTrashRestController;
import com.scripty.controller.SongVersionRestController;
import com.scripty.controller.TextDocumentRestController;
import com.scripty.dto.SongBlock;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.viewmodel.songblock.SongBlockViewModel;
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
 * Builds HAL resources for a song's lyric blocks, the song counterpart of
 * {@link BlockResourceAssembler}. Mutation links (update, delete, createBelow,
 * move, setHighlight) appear only when the current user can edit the song's
 * project — the client gates its UI on their presence, the same rule the block
 * and document assemblers use.
 *
 * <p>Like {@link SongVersionResourceAssembler} it does not implement
 * {@link org.springframework.hateoas.server.RepresentationModelAssembler}: a
 * song block only knows its document, so the owning project id has to be passed
 * in for the links and the edit check.
 */
@Component
public class SongBlockResourceAssembler {

    @Autowired
    ProjectAccessSupport projectAccess;

    public EntityModel<SongBlockResource> toModel(SongBlockViewModel block, Integer projectId) {
        SongBlockResource resource = toResource(block);
        resource.setProjectId(projectId);
        return EntityModel.of(resource).add(blockLinks(block.getId(), block.getDocumentId(), projectId));
    }

    public EntityModel<SongBlockResource> toModel(SongBlock block, Integer projectId) {
        SongBlockResource resource = toResource(block);
        resource.setProjectId(projectId);
        return EntityModel.of(resource).add(blockLinks(block.getId(), resource.getDocumentId(), projectId));
    }

    /** The deleted block is gone, so the response points back at what survives it. */
    public EntityModel<SongBlockResource> toDeleteModel(Integer documentId, Integer projectId) {
        SongBlockResource resource = new SongBlockResource();
        resource.setDocumentId(documentId);
        resource.setProjectId(projectId);
        return EntityModel.of(resource).add(documentLinks(documentId, projectId, true));
    }

    public CollectionModel<EntityModel<SongBlockResource>> toCollection(
            List<SongBlockViewModel> blocks, Integer documentId, Integer projectId) {
        List<EntityModel<SongBlockResource>> resources = new ArrayList<>();
        for (SongBlockViewModel block : blocks) {
            resources.add(toModel(block, projectId));
        }
        Link self = linkTo(methodOn(SongBlockRestController.class).list(documentId, null, null)).withSelfRel();
        if (canEdit(projectId)) {
            self = self.andAffordance(afford(methodOn(SongBlockRestController.class).append(documentId, null, null)));
        }
        CollectionModel<EntityModel<SongBlockResource>> collection = CollectionModel.of(resources)
                .add(self)
                .add(documentLinks(documentId, projectId, false));
        // Reading what was cut needs only access to the song, the same rule the
        // web editor's recovery page follows, so this sits outside the gate.
        collection.add(linkTo(methodOn(SongBlockTrashRestController.class).list(documentId, null))
                .withRel(ApiRel.TRASH));
        if (canEdit(projectId)) {
            collection.add(linkTo(methodOn(SongBlockRestController.class).append(documentId, null, null))
                    .withRel(ApiRel.CREATE));
            // Only an editor has a stack to walk: the checkpoints are recorded
            // by their own edits, so offering this to a reader would advertise
            // an undo of somebody else's typing.
            collection.add(linkTo(methodOn(SongBlockRestController.class)
                    .undoRedoStatus(documentId, null, null)).withRel(ApiRel.UNDO_REDO_STATUS));
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

    private SongBlockResource toResource(SongBlock block) {
        SongBlockResource resource = new SongBlockResource();
        resource.setId(block.getId());
        if (block.getTextDocument() != null) {
            resource.setDocumentId(block.getTextDocument().getId());
        }
        resource.setOrder(block.getOrder());
        resource.setContent(block.getContent());
        resource.setHighlight(block.getHighlight());
        return resource;
    }

    private Link[] blockLinks(Integer id, Integer documentId, Integer projectId) {
        List<Link> links = new ArrayList<>();
        Link self = linkTo(methodOn(SongBlockRestController.class).show(id, null)).withSelfRel();
        if (canEdit(projectId)) {
            // HAL-FORMS clients read the how (method + fields) off the self link.
            self = self
                    .andAffordance(afford(methodOn(SongBlockRestController.class).update(id, null, null)))
                    .andAffordance(afford(methodOn(SongBlockRestController.class).delete(id, null)));
        }
        links.add(self);
        if (canEdit(projectId)) {
            links.add(linkTo(methodOn(SongBlockRestController.class).update(id, null, null))
                    .withRel(ApiRel.UPDATE));
            links.add(linkTo(methodOn(SongBlockRestController.class).delete(id, null))
                    .withRel(ApiRel.DELETE));
            links.add(linkTo(methodOn(SongBlockRestController.class).createBelow(id, null, null))
                    .withRel(ApiRel.CREATE_BELOW));
            links.add(linkTo(methodOn(SongBlockRestController.class).move(id, null, null))
                    .withRel(ApiRel.MOVE));
            links.add(linkTo(methodOn(SongBlockRestController.class).setHighlight(id, null, null))
                    .withRel(ApiRel.SET_HIGHLIGHT));
        }
        links.addAll(List.of(documentLinks(documentId, projectId, true)));
        return links.toArray(Link[]::new);
    }

    /**
     * Links from a block or the block list back up to the song and its project.
     * The block list is its own {@code songBlocks} collection, so it passes
     * {@code false} for {@code includeSiblings} rather than repeat its self link.
     */
    private Link[] documentLinks(Integer documentId, Integer projectId, boolean includeSiblings) {
        List<Link> links = new ArrayList<>();
        if (documentId != null) {
            if (includeSiblings) {
                links.add(linkTo(methodOn(SongBlockRestController.class).list(documentId, null, null))
                        .withRel(ApiRel.SONG_BLOCKS));
            }
            links.add(linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                    .withRel(ApiRel.SONG));
            links.add(linkTo(methodOn(SongVersionRestController.class).list(documentId, null, null))
                    .withRel(ApiRel.VERSIONS));
        }
        if (projectId != null) {
            links.add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                    .withRel(ApiRel.PROJECT));
        }
        return links.toArray(Link[]::new);
    }

    private boolean canEdit(Integer projectId) {
        return projectAccess.canEditScriptForCurrentUser(projectId);
    }
}
