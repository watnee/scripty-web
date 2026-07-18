package com.scripty.api;

import com.scripty.controller.BlockController;
import com.scripty.controller.ProjectController;
import com.scripty.controller.ProjectRestController;
import com.scripty.controller.SongBlockController;
import com.scripty.controller.SongBlockRestController;
import com.scripty.controller.TextDocumentRestController;
import java.util.Map;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

public final class HypermediaSupport {

    private HypermediaSupport() {
    }

    public static EntityModel<Map<String, Object>> projectSyncStatus(Map<String, Object> body, Integer projectId, Long since) {
        return projectSyncStatus(body, projectId, since, null);
    }

    public static EntityModel<Map<String, Object>> projectSyncStatus(Map<String, Object> body, Integer projectId, Long since, Integer editionId) {
        return EntityModel.of(body).add(projectSyncLinks(projectId, since, editionId));
    }

    public static EntityModel<Map<String, Object>> projectUndoRedo(Map<String, Object> body, Integer projectId, boolean undo) {
        Link self = undo
                ? linkTo(methodOn(ProjectController.class).undo(projectId, null, null)).withSelfRel()
                : linkTo(methodOn(ProjectController.class).redo(projectId, null, null)).withSelfRel();
        return EntityModel.of(body).add(self).add(projectActionLinks(projectId));
    }

    public static EntityModel<Map<String, Object>> projectUndoRedoStatus(Map<String, Object> body, Integer projectId) {
        return EntityModel.of(body)
                .add(linkTo(methodOn(ProjectController.class).undoRedoStatus(projectId, null, null)).withSelfRel())
                .add(projectActionLinks(projectId));
    }

    public static EntityModel<Map<String, Boolean>> blockToggle(Map<String, Boolean> body, Integer blockId, boolean bookmark) {
        Link self = bookmark
                ? linkTo(methodOn(BlockController.class).toggleBookmarkInline(blockId, null)).withSelfRel()
                : linkTo(methodOn(BlockController.class).togglePinnedInline(blockId, null)).withSelfRel();
        Link alternate = bookmark
                ? linkTo(methodOn(BlockController.class).togglePinnedInline(blockId, null)).withRel(ApiRel.TOGGLE_PINNED)
                : linkTo(methodOn(BlockController.class).toggleBookmarkInline(blockId, null)).withRel(ApiRel.TOGGLE_BOOKMARK);
        return EntityModel.of(body, self, alternate);
    }

    /**
     * The song editor's undo/redo status, given the same link set the
     * screenplay's {@link #projectUndoRedoStatus} gets. The undo and redo
     * targets render HTMX fragments rather than HAL, but they are still the
     * transitions a client takes from here, so they are advertised as links.
     */
    public static EntityModel<Map<String, Object>> songUndoRedoStatus(Map<String, Object> body,
                                                                      Integer documentId, Integer editionId) {
        return EntityModel.of(body).add(
                linkTo(methodOn(SongBlockController.class).undoRedoStatus(documentId, editionId, null)).withSelfRel(),
                linkTo(methodOn(TextDocumentRestController.class).show(documentId, null)).withRel(ApiRel.SONG),
                linkTo(methodOn(SongBlockRestController.class).list(documentId, editionId, null))
                        .withRel(ApiRel.SONG_BLOCKS),
                songAction("undo", ApiRel.UNDO, documentId, editionId),
                songAction("redo", ApiRel.REDO, documentId, editionId));
    }

    /**
     * Links to a song editor action by path rather than by {@code methodOn}:
     * those handlers return a view name, and the dummy-invocation proxy cannot
     * subclass {@code String} to record the call.
     */
    private static Link songAction(String path, String rel, Integer documentId, Integer editionId) {
        UriComponentsBuilder uri = UriComponentsBuilder
                .fromUriString(linkTo(SongBlockController.class).toUri().toString())
                .pathSegment(path)
                .queryParam("documentId", documentId);
        if (editionId != null) {
            uri = uri.queryParam("editionId", editionId);
        }
        return Link.of(uri.toUriString(), rel);
    }

    private static Link[] projectSyncLinks(Integer projectId, Long since, Integer editionId) {
        return new Link[]{
                linkTo(methodOn(ProjectController.class).syncStatus(projectId, since, editionId, null)).withSelfRel(),
                linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT),
                linkTo(methodOn(ProjectController.class).undoRedoStatus(projectId, null, null)).withRel(ApiRel.UNDO_REDO_STATUS)
        };
    }

    private static Link[] projectActionLinks(Integer projectId) {
        return new Link[]{
                linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT),
                linkTo(methodOn(ProjectController.class).undo(projectId, null, null)).withRel(ApiRel.UNDO),
                linkTo(methodOn(ProjectController.class).redo(projectId, null, null)).withRel(ApiRel.REDO),
                linkTo(methodOn(ProjectController.class).undoRedoStatus(projectId, null, null)).withRel(ApiRel.UNDO_REDO_STATUS),
                linkTo(methodOn(ProjectController.class).syncStatus(projectId, null, null, null)).withRel(ApiRel.SYNC_STATUS)
        };
    }
}
