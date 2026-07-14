package com.scripty.controller;

import com.scripty.api.BlockResource;
import com.scripty.api.BlockResourceAssembler;
import com.scripty.api.ApiError;
import com.scripty.api.RestErrors;
import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.repository.BlockRepository;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.BlockService;
import com.scripty.service.ScriptEditionService;
import com.scripty.dto.ScriptEdition;
import com.scripty.viewmodel.block.BlockViewModel;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> list(
            @RequestParam Integer projectId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.forbidden());
        }
        boolean canBrowseEditions = projectAccess.canEditScript(projectId, principal);
        ScriptEdition edition = scriptEditionService.resolveForAccess(projectId, editionId, canBrowseEditions);
        List<Block> blocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAsc(projectId);
        List<BlockViewModel> viewModels = new ArrayList<>();
        for (Block block : blocks) {
            viewModels.add(blockService.getBlockViewModel(block.getId()));
        }
        return ResponseEntity.ok(blockResourceAssembler.toBlockCollection(viewModels, projectId));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateBlockCommandModel commandModel,
            BindingResult bindingResult,
            Principal principal) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        if (!projectAccess.canEditScript(commandModel.getProjectId(), principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.forbidden());
        }
        Block block = blockService.saveCreateBlockCommandModel(commandModel);
        EntityModel<BlockResource> resource = blockResourceAssembler.toModel(block);
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> show(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canAccessBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.forbidden());
        }
        Block block = blockService.read(id);
        if (block == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.notFound());
        }
        BlockViewModel viewModel = blockService.getBlockViewModel(id);
        Integer projectId = block.getProject() != null ? block.getProject().getId() : null;
        return ResponseEntity.ok(blockResourceAssembler.toModel(viewModel, projectId));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @Valid @RequestBody EditBlockCommandModel commandModel,
            BindingResult bindingResult,
            Principal principal) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.forbidden());
        }
        commandModel.setId(id);
        Block block = blockService.saveEditBlockCommandModel(commandModel);
        return ResponseEntity.ok(blockResourceAssembler.toModel(block));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> delete(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.forbidden());
        }
        Block block = blockService.deleteBlock(id);
        return ResponseEntity.ok(blockResourceAssembler.toDeleteModel(block));
    }

    @RequestMapping(value = "/{id}/bookmark", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> toggleBookmark(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.forbidden());
        }
        Block block = blockService.toggleBookmark(id);
        return ResponseEntity.ok(blockResourceAssembler.toModel(block));
    }

    @RequestMapping(value = "/{id}/pinned", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> togglePinned(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.forbidden());
        }
        Block block = blockService.togglePinned(id);
        return ResponseEntity.ok(blockResourceAssembler.toModel(block));
    }
}
