package com.scripty.api;

import com.scripty.controller.BlockController;
import com.scripty.controller.ProjectController;
import com.scripty.controller.ProjectRestController;
import java.util.Map;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

public final class HypermediaSupport {

    private HypermediaSupport() {
    }

    public static EntityModel<Map<String, Object>> projectSyncStatus(Map<String, Object> body, Integer projectId, Long since) {
        return EntityModel.of(body).add(projectSyncLinks(projectId, since));
    }

    public static EntityModel<Map<String, Object>> projectUndoRedo(Map<String, Object> body, Integer projectId, boolean undo) {
        Link self = undo
                ? linkTo(methodOn(ProjectController.class).undo(projectId, null)).withSelfRel()
                : linkTo(methodOn(ProjectController.class).redo(projectId, null)).withSelfRel();
        return EntityModel.of(body).add(self).add(projectActionLinks(projectId));
    }

    public static EntityModel<Map<String, Object>> projectUndoRedoStatus(Map<String, Object> body, Integer projectId) {
        return EntityModel.of(body)
                .add(linkTo(methodOn(ProjectController.class).undoRedoStatus(projectId, null)).withSelfRel())
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

    private static Link[] projectSyncLinks(Integer projectId, Long since) {
        return new Link[]{
                linkTo(methodOn(ProjectController.class).syncStatus(projectId, since, null)).withSelfRel(),
                linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT),
                linkTo(methodOn(ProjectController.class).undoRedoStatus(projectId, null)).withRel(ApiRel.UNDO_REDO_STATUS)
        };
    }

    private static Link[] projectActionLinks(Integer projectId) {
        return new Link[]{
                linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT),
                linkTo(methodOn(ProjectController.class).undo(projectId, null)).withRel(ApiRel.UNDO),
                linkTo(methodOn(ProjectController.class).redo(projectId, null)).withRel(ApiRel.REDO),
                linkTo(methodOn(ProjectController.class).undoRedoStatus(projectId, null)).withRel(ApiRel.UNDO_REDO_STATUS),
                linkTo(methodOn(ProjectController.class).syncStatus(projectId, null, null)).withRel(ApiRel.SYNC_STATUS)
        };
    }
}
