package com.scripty.api;

import com.scripty.controller.BlockController;
import com.scripty.controller.ProjectController;
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
                ? linkTo(methodOn(ProjectController.class).undo(projectId)).withSelfRel()
                : linkTo(methodOn(ProjectController.class).redo(projectId)).withSelfRel();
        return EntityModel.of(body).add(self).add(projectActionLinks(projectId));
    }

    public static EntityModel<Map<String, Object>> projectUndoRedoStatus(Map<String, Object> body, Integer projectId) {
        return EntityModel.of(body)
                .add(linkTo(methodOn(ProjectController.class).undoRedoStatus(projectId)).withSelfRel())
                .add(projectActionLinks(projectId));
    }

    public static EntityModel<Map<String, Boolean>> blockToggle(Map<String, Boolean> body, Integer blockId, boolean bookmark) {
        Link self = bookmark
                ? linkTo(methodOn(BlockController.class).toggleBookmarkInline(blockId)).withSelfRel()
                : linkTo(methodOn(BlockController.class).togglePinnedInline(blockId)).withSelfRel();
        Link alternate = bookmark
                ? linkTo(methodOn(BlockController.class).togglePinnedInline(blockId)).withRel(ApiRel.TOGGLE_PINNED)
                : linkTo(methodOn(BlockController.class).toggleBookmarkInline(blockId)).withRel(ApiRel.TOGGLE_BOOKMARK);
        return EntityModel.of(body, self, alternate);
    }

    private static Link[] projectSyncLinks(Integer projectId, Long since) {
        return new Link[]{
                linkTo(methodOn(ProjectController.class).syncStatus(projectId, since)).withSelfRel(),
                linkTo(methodOn(ProjectController.class).show(projectId, null, null)).withRel(ApiRel.PROJECT),
                linkTo(methodOn(ProjectController.class).undoRedoStatus(projectId)).withRel(ApiRel.UNDO_REDO_STATUS)
        };
    }

    private static Link[] projectActionLinks(Integer projectId) {
        return new Link[]{
                linkTo(methodOn(ProjectController.class).show(projectId, null, null)).withRel(ApiRel.PROJECT),
                linkTo(methodOn(ProjectController.class).undo(projectId)).withRel(ApiRel.UNDO),
                linkTo(methodOn(ProjectController.class).redo(projectId)).withRel(ApiRel.REDO),
                linkTo(methodOn(ProjectController.class).undoRedoStatus(projectId)).withRel(ApiRel.UNDO_REDO_STATUS),
                linkTo(methodOn(ProjectController.class).syncStatus(projectId, null)).withRel(ApiRel.SYNC_STATUS)
        };
    }
}
