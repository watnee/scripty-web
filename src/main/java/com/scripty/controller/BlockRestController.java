package com.scripty.controller;

import com.scripty.api.BlockResource;
import com.scripty.api.BlockResourceAssembler;
import com.scripty.api.BulkAddTagsRequest;
import com.scripty.api.BulkBlockRequest;
import com.scripty.api.BulkDeleteRequest;
import com.scripty.api.BulkFormatRequest;
import com.scripty.api.BulkReplaceRequest;
import com.scripty.api.BulkSetTypeRequest;
import com.scripty.api.CreateBlockBelowRequest;
import com.scripty.api.MoveBlockRequest;
import com.scripty.api.RestErrors;
import com.scripty.api.SetBlockTypeRequest;
import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.repository.BlockRepository;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.BlockService;
import com.scripty.service.ProjectUndoRedoService;
import com.scripty.service.ProjectVersionService;
import com.scripty.service.ScriptEditionService;
import com.scripty.util.BlockFormatting;
import com.scripty.dto.ScriptEdition;
import com.scripty.viewmodel.block.BlockViewModel;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/block")
public class BlockRestController {

    @Autowired
    BlockService blockService;

    @Autowired
    BlockRepository blockRepository;

    @Autowired
    BlockResourceAssembler blockResourceAssembler;

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    ScriptEditionService scriptEditionService;

    @Autowired
    ProjectUndoRedoService projectUndoRedoService;

    @Autowired
    ProjectVersionService projectVersionService;

    /**
     * Records an undo checkpoint for the block's project.
     *
     * <p>Undo state is kept per (project, edition). API clients read their status
     * from {@code /project/undoRedoStatus?projectId=N} without naming an edition,
     * so checkpoints are recorded against the project — the edition-scoped
     * variants would land under a key those clients never read, leaving undo
     * permanently unavailable. A null edition still snapshots the default
     * edition, so the restored state is the same either way.
     *
     * <p>Call before mutating, so the snapshot captures the pre-edit state.
     */
    private void recordCheckpointFor(Integer blockId) {
        Block block = blockService.read(blockId);
        if (block != null && block.getProject() != null) {
            projectUndoRedoService.recordCheckpoint(block.getProject().getId());
        }
    }

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<CollectionModel<EntityModel<BlockResource>>> list(
            @RequestParam Integer projectId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        boolean canBrowseEditions = projectAccess.canEditScript(projectId, principal);
        ScriptEdition edition = scriptEditionService.resolveForAccess(projectId, editionId, canBrowseEditions);
        List<Block> blocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAscIdAsc(projectId);
        List<BlockViewModel> viewModels = new ArrayList<>();
        for (Block block : blocks) {
            viewModels.add(blockService.getBlockViewModel(block.getId()));
        }
        return ResponseEntity.ok(blockResourceAssembler.toBlockCollection(viewModels, projectId));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateBlockCommandModel commandModel,
            BindingResult bindingResult,
            Principal principal) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        if (!projectAccess.canEditScript(commandModel.getProjectId(), principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        projectUndoRedoService.recordCheckpoint(commandModel.getProjectId());
        Block block = blockService.saveCreateBlockCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());
        EntityModel<BlockResource> resource = blockResourceAssembler.toModel(block);
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<BlockResource>> show(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canAccessBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Block block = blockService.read(id);
        if (block == null) {
            return ResponseEntity.notFound().build();
        }
        BlockViewModel viewModel = blockService.getBlockViewModel(id);
        Integer projectId = block.getProject() != null ? block.getProject().getId() : null;
        return ResponseEntity.ok(blockResourceAssembler.toModel(viewModel, projectId));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @Valid @RequestBody EditBlockCommandModel commandModel,
            BindingResult bindingResult,
            Principal principal) {
        validateFormatting(commandModel, bindingResult);
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        commandModel.setId(id);
        recordCheckpointFor(id);
        Block block = blockService.saveEditBlockCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());
        return ResponseEntity.ok(blockResourceAssembler.toModel(block));
    }

    /**
     * Rejects alignment/font values outside the sets the editor offers. Omitted
     * (null) formatting is always fine — it means "leave what is stored".
     */
    private static void validateFormatting(EditBlockCommandModel commandModel, BindingResult bindingResult) {
        if (commandModel.getTextAlign() != null
                && BlockFormatting.normalizeAlign(commandModel.getTextAlign()) == null) {
            bindingResult.rejectValue("textAlign", "textAlign.invalid",
                    "Text align must be one of left, center, or right.");
        }
        if (commandModel.getFont() != null
                && BlockFormatting.normalizeFont(commandModel.getFont()) == null) {
            bindingResult.rejectValue("font", "font.invalid",
                    "Font must be one of Courier Prime, Arial, or Times New Roman.");
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<BlockResource>> delete(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        recordCheckpointFor(id);
        Block block = blockService.deleteBlock(id);
        projectVersionService.autoSaveVersion(block.getProject().getId());
        return ResponseEntity.ok(blockResourceAssembler.toDeleteModel(block));
    }

    @RequestMapping(value = "/{id}/bookmark", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<BlockResource>> toggleBookmark(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Block block = blockService.toggleBookmark(id);
        return ResponseEntity.ok(blockResourceAssembler.toModel(block));
    }

    @RequestMapping(value = "/{id}/pinned", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<BlockResource>> togglePinned(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Block block = blockService.togglePinned(id);
        return ResponseEntity.ok(blockResourceAssembler.toModel(block));
    }

    /**
     * Inserts a block directly below {@code id}, the way pressing Enter does in
     * the web editor. Content may be blank — the caller typically inserts an
     * empty element and fills it in as the writer types.
     */
    @RequestMapping(value = "/{id}/below", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> createBelow(
            @PathVariable Integer id,
            @RequestBody CreateBlockBelowRequest request,
            Principal principal) {
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (blockService.read(id) == null) {
            return ResponseEntity.notFound().build();
        }

        CreateBlockBelowCommandModel commandModel = new CreateBlockBelowCommandModel();
        commandModel.setId(id);
        commandModel.setContent(request.content() != null ? request.content() : "");
        commandModel.setPersonId(request.personId());
        commandModel.setType(request.type());

        recordCheckpointFor(id);
        Block block = blockService.saveCreateBlockBelowCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());

        EntityModel<BlockResource> resource = blockResourceAssembler.toModel(block);
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    /**
     * Creates the single empty element an untouched script needs before there is
     * anything to type into, or to insert below. Returns 409 if the script
     * already has blocks — those callers want createBelow.
     */
    @RequestMapping(value = "/initial", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> createInitial(@RequestParam Integer projectId, Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        projectUndoRedoService.recordCheckpoint(projectId);
        Block block = blockService.createInitialBlock(projectId);
        if (block == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        projectVersionService.autoSaveVersionForBlock(block.getId());

        EntityModel<BlockResource> resource = blockResourceAssembler.toModel(block);
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    /**
     * Retypes a block (Scene, Action, Character, …), the REST counterpart of the
     * web editor's element-type bar. Omitting content or tags keeps the stored
     * values.
     */
    @RequestMapping(value = "/{id}/type", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> setType(
            @PathVariable Integer id,
            @RequestBody SetBlockTypeRequest request,
            Principal principal) {
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.type() == null || request.type().isBlank()) {
            return new ResponseEntity<>(Map.of("type", "You must supply a value for Type."), HttpStatus.BAD_REQUEST);
        }

        recordCheckpointFor(id);
        Block block = blockService.updateBlockTypeAndContent(
                id, request.type(), request.content(), request.personId(), request.tags());
        if (block == null) {
            return ResponseEntity.notFound().build();
        }
        projectVersionService.autoSaveVersionForBlock(block.getId());
        return ResponseEntity.ok(blockResourceAssembler.toModel(block));
    }

    // ---------------------------------------------------------------------
    // Bulk operations
    //
    // The web editor has had these since the selection toolbar landed, but only
    // as form-encoded MVC endpoints that answer with a 302 to an HTML page —
    // unusable from an API client. These are the REST counterparts.
    //
    // Each records exactly one undo checkpoint for the whole batch, which is
    // the point of having them: retyping twenty elements should be one press of
    // undo, not twenty. Looping the per-block endpoints cannot achieve that.
    //
    // All five answer with the project's refreshed block collection, so a
    // client applies the result in a single round trip instead of re-fetching.
    // ---------------------------------------------------------------------

    /**
     * Shared guard: a bulk call needs a non-empty id list, a project, and edit
     * rights over every block named — checked against the project the caller
     * supplied, so a caller cannot reach blocks outside it.
     *
     * @return the error response to return, or null when the call may proceed
     */
    private ResponseEntity<?> denyBulk(BulkBlockRequest request, Principal principal) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return new ResponseEntity<>(
                    Map.of("ids", "You must supply at least one block id."), HttpStatus.BAD_REQUEST);
        }
        if (request.ids().contains(null)) {
            return new ResponseEntity<>(
                    Map.of("ids", "Block ids must not be null."), HttpStatus.BAD_REQUEST);
        }
        if (request.projectId() == null) {
            return new ResponseEntity<>(
                    Map.of("projectId", "You must supply a value for Project."), HttpStatus.BAD_REQUEST);
        }
        if (!projectAccess.canEditScript(request.projectId(), principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!projectAccess.canEditBlocks(
                request.ids(), request.projectId(), projectAccess.currentUser(principal))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return null;
    }

    /** The collection every bulk operation answers with, after the batch ran. */
    private ResponseEntity<CollectionModel<EntityModel<BlockResource>>> refreshed(
            Integer projectId, Principal principal) {
        return list(projectId, null, principal);
    }

    @RequestMapping(value = "/bulk/type", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> bulkSetType(
            @RequestBody BulkSetTypeRequest request, Principal principal) {
        ResponseEntity<?> denied = denyBulk(request, principal);
        if (denied != null) {
            return denied;
        }
        if (request.type() == null || request.type().isBlank()) {
            return new ResponseEntity<>(
                    Map.of("type", "You must supply a value for Type."), HttpStatus.BAD_REQUEST);
        }
        projectUndoRedoService.recordCheckpoint(request.projectId());
        blockService.setBlockTypes(request.ids(), request.type());
        projectVersionService.autoSaveVersion(request.projectId());
        return refreshed(request.projectId(), principal);
    }

    @RequestMapping(value = "/bulk/tags", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> bulkAddTags(
            @RequestBody BulkAddTagsRequest request, Principal principal) {
        ResponseEntity<?> denied = denyBulk(request, principal);
        if (denied != null) {
            return denied;
        }
        if (request.tags() == null || request.tags().isBlank()) {
            return new ResponseEntity<>(
                    Map.of("tags", "You must supply a value for Tags."), HttpStatus.BAD_REQUEST);
        }
        projectUndoRedoService.recordCheckpoint(request.projectId());
        blockService.addTagsToBlocks(request.ids(), request.tags());
        projectVersionService.autoSaveVersion(request.projectId());
        return refreshed(request.projectId(), principal);
    }

    @RequestMapping(value = "/bulk/delete", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> bulkDelete(
            @RequestBody BulkDeleteRequest request, Principal principal) {
        ResponseEntity<?> denied = denyBulk(request, principal);
        if (denied != null) {
            return denied;
        }
        projectUndoRedoService.recordCheckpoint(request.projectId());
        blockService.deleteBlocks(request.ids());
        projectVersionService.autoSaveVersion(request.projectId());
        return refreshed(request.projectId(), principal);
    }

    /**
     * Applies alignment, font, a style toggle and/or a highlight in one batch.
     * Unknown alignment and font values are rejected, matching the per-block
     * update; an unknown highlight clears the tint, matching the service.
     */
    @RequestMapping(value = "/bulk/format", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> bulkFormat(
            @RequestBody BulkFormatRequest request, Principal principal) {
        ResponseEntity<?> denied = denyBulk(request, principal);
        if (denied != null) {
            return denied;
        }
        if (request.isEmpty()) {
            return new ResponseEntity<>(
                    Map.of("format", "You must supply at least one of align, font, style, or highlight."),
                    HttpStatus.BAD_REQUEST);
        }
        if (request.hasAlign() && BlockFormatting.normalizeAlign(request.align()) == null) {
            return new ResponseEntity<>(
                    Map.of("align", "Text align must be one of left, center, or right."),
                    HttpStatus.BAD_REQUEST);
        }
        if (request.hasFont() && BlockFormatting.normalizeFont(request.font()) == null) {
            return new ResponseEntity<>(
                    Map.of("font", "Font must be one of Courier Prime, Arial, or Times New Roman."),
                    HttpStatus.BAD_REQUEST);
        }
        if (request.hasStyle() && !Block.TEXT_STYLES.contains(canonicalStyle(request.style()))) {
            return new ResponseEntity<>(
                    Map.of("style", "Style must be one of bold, italic, or underline."),
                    HttpStatus.BAD_REQUEST);
        }

        projectUndoRedoService.recordCheckpoint(request.projectId());
        if (request.hasAlign()) {
            blockService.setBlockAlignments(request.ids(), request.align());
        }
        if (request.hasFont()) {
            blockService.setBlockFonts(request.ids(), request.font());
        }
        if (request.hasStyle()) {
            blockService.toggleBlockTextStyles(request.ids(), request.style());
        }
        if (request.hasHighlight()) {
            blockService.setBlockHighlights(request.ids(), request.resolvedHighlight());
        }
        projectVersionService.autoSaveVersion(request.projectId());
        return refreshed(request.projectId(), principal);
    }

    private static String canonicalStyle(String style) {
        return style == null ? null : style.trim().toUpperCase();
    }

    /**
     * Find and replace across a set of blocks.
     *
     * <p>Answers with the refreshed collection like every other bulk call. The
     * service's changed-block count is deliberately not surfaced: a caller can
     * see exactly what changed by comparing the returned blocks with the ones
     * it already held, and inventing a side channel for one number would make
     * this the only bulk endpoint whose result is not just the collection.
     */
    @RequestMapping(value = "/bulk/replace", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> bulkReplace(
            @RequestBody BulkReplaceRequest request, Principal principal) {
        ResponseEntity<?> denied = denyBulk(request, principal);
        if (denied != null) {
            return denied;
        }
        if (request.find() == null || request.find().isEmpty()) {
            return new ResponseEntity<>(
                    Map.of("find", "You must supply a value to find."), HttpStatus.BAD_REQUEST);
        }

        projectUndoRedoService.recordCheckpoint(request.projectId());
        blockService.replaceInBlocks(
                request.ids(),
                request.find(),
                request.replacementOrEmpty(),
                request.matchCaseOrFalse(),
                request.wholeWordOrFalse(),
                request.includeCharacterCuesOrFalse());
        projectVersionService.autoSaveVersion(request.projectId());
        return refreshed(request.projectId(), principal);
    }

    /**
     * Reorders a block to an absolute {@code position}, matching the order values
     * reported by the block collection.
     */
    @RequestMapping(value = "/{id}/move", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> move(
            @PathVariable Integer id,
            @RequestBody MoveBlockRequest request,
            Principal principal) {
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.position() == null) {
            return new ResponseEntity<>(Map.of("position", "You must supply a value for Position."), HttpStatus.BAD_REQUEST);
        }
        Block current = blockService.read(id);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }

        int fromOrder = current.getOrder();
        if (fromOrder == request.position()) {
            return ResponseEntity.ok(blockResourceAssembler.toModel(current));
        }

        // A full snapshot rather than recordMoveCheckpoint: the lightweight move
        // record is edition-keyed, which API clients cannot undo. See
        // recordCheckpointFor.
        recordCheckpointFor(id);
        Block block = blockService.moveBlockTo(id, request.position());
        projectVersionService.autoSaveVersionForBlock(block.getId());
        return ResponseEntity.ok(blockResourceAssembler.toModel(block));
    }
}
