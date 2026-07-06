/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.controller;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.viewmodel.block.createblock.CreateBlockViewModel;
import com.scripty.viewmodel.block.createblockbelow.CreateBlockBelowViewModel;
import com.scripty.viewmodel.block.editblock.EditBlockViewModel;
import com.scripty.viewmodel.scene.sceneprofile.BlockViewModel;
import com.scripty.service.BlockService;
import com.scripty.service.ProjectUndoRedoService;
import com.scripty.service.ProjectVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author chris
 */
@Controller
@RequestMapping(value = "/block")
public class BlockController {
    
    @Autowired
    BlockService blockService;

    @Autowired
    ProjectVersionService projectVersionService;

    @Autowired
    ProjectUndoRedoService projectUndoRedoService;
    
    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id, @RequestParam(required = false) Integer projectId) {
        projectUndoRedoService.recordCheckpointForBlock(id);
        Block block = blockService.read(id);
        Integer resolvedProjectId = projectId;
        if (resolvedProjectId == null && block != null && block.getScene() != null) {
            resolvedProjectId = block.getScene().getProject().getId();
        }
        block = blockService.deleteBlock(id);
        if (resolvedProjectId != null) {
            projectVersionService.autoSaveVersion(resolvedProjectId);
        }

        if (projectId != null) {
            return "redirect:/project/show?id=" + projectId;
        }
        return "redirect:/scene/show?id=" + block.getScene().getId();
    }
    
    @RequestMapping(value = "/moveUp")
    public String moveUp(@RequestParam Integer id, @RequestParam(required = false) Integer projectId) {
        Block block = blockService.read(id);
        if (block == null) {
            return "redirect:/project/list";
        }
        int fromOrder = block.getOrder();
        Block moved = blockService.moveBlockUp(id);
        if (moved != null && moved.getOrder() != fromOrder) {
            projectUndoRedoService.recordMoveCheckpoint(id, fromOrder, moved.getOrder());
            projectVersionService.autoSaveVersionForBlock(moved.getId());
        }
        if (projectId != null) {
            return "redirect:/project/show?id=" + projectId;
        }
        return "redirect:/scene/show?id=" + moved.getScene().getId();
    }

    @RequestMapping(value = "/moveDown")
    public String moveDown(@RequestParam Integer id, @RequestParam(required = false) Integer projectId) {
        Block block = blockService.read(id);
        if (block == null) {
            return "redirect:/project/list";
        }
        int fromOrder = block.getOrder();
        Block moved = blockService.moveBlockDown(id);
        if (moved != null && moved.getOrder() != fromOrder) {
            projectUndoRedoService.recordMoveCheckpoint(id, fromOrder, moved.getOrder());
            projectVersionService.autoSaveVersionForBlock(moved.getId());
        }
        if (projectId != null) {
            return "redirect:/project/show?id=" + projectId;
        }
        return "redirect:/scene/show?id=" + moved.getScene().getId();
    }

    @RequestMapping(value = "/moveTo", method = RequestMethod.POST)
    public String moveTo(@RequestParam Integer id, @RequestParam int position,
                         @RequestParam(required = false) Integer projectId) {
        Block block = blockService.read(id);
        if (block == null) {
            if (projectId != null) {
                return "redirect:/project/show?id=" + projectId;
            }
            return "redirect:/project/list";
        }
        if (block.getOrder() != position) {
            projectUndoRedoService.recordMoveCheckpoint(id, block.getOrder(), position);
            block = blockService.moveBlockTo(id, position);
            projectVersionService.autoSaveVersionForBlock(block.getId());
        }
        if (projectId != null) {
            return "redirect:/project/show?id=" + projectId;
        }
        return "redirect:/scene/show?id=" + block.getScene().getId();
    }

    @RequestMapping(value = "/toggleBookmark")
    public String toggleBookmark(@RequestParam Integer id, @RequestParam(required = false) Integer projectId) {
        Block block = blockService.toggleBookmark(id);
        if (projectId != null) {
            return "redirect:/project/show?id=" + projectId;
        }
        return "redirect:/scene/show?id=" + block.getScene().getId();
    }

    @RequestMapping(value = "/togglePinned")
    public String togglePinned(@RequestParam Integer id, @RequestParam(required = false) Integer projectId) {
        Block block = blockService.togglePinned(id);
        if (projectId != null) {
            return "redirect:/project/show?id=" + projectId;
        }
        return "redirect:/scene/show?id=" + block.getScene().getId();
    }

    @RequestMapping(value = "/editInline")
    public String editInline(@RequestParam Integer id, Model model) {
        EditBlockViewModel viewModel = blockService.getEditBlockViewModel(id);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditBlockCommandModel());
        model.addAttribute("block", blockService.getBlockViewModel(id));
        return "block/editInline";
    }

    @RequestMapping(value = "/editInline", method = RequestMethod.POST)
    public String saveEditInline(@Valid @ModelAttribute("commandModel") EditBlockCommandModel commandModel, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            EditBlockViewModel viewModel = blockService.getEditBlockViewModel(commandModel.getId());
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);
            model.addAttribute("block", blockService.getBlockViewModel(commandModel.getId()));
            return "block/editInline";
        }
        projectUndoRedoService.recordCheckpointForBlock(commandModel.getId());
        Block block = blockService.saveEditBlockCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());
        BlockViewModel vm = blockService.getBlockViewModel(block.getId());
        model.addAttribute("block", vm);
        return "block/showInline";
    }

    @RequestMapping(value = "/showInline")
    public String showInline(@RequestParam Integer id, Model model) {
        BlockViewModel vm = blockService.getBlockViewModel(id);
        model.addAttribute("block", vm);
        return "block/showInline";
    }

    // Show Form
    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model) {

        EditBlockViewModel viewModel = blockService.getEditBlockViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditBlockCommandModel());

        return "block/edit";
    }

    // Handle Form Submission
    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditBlockCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            EditBlockViewModel viewModel = blockService.getEditBlockViewModel(commandModel.getId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "block/edit";
        }

        projectUndoRedoService.recordCheckpointForBlock(commandModel.getId());
        Block block = blockService.saveEditBlockCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());

        return "redirect:/scene/show?id=" + block.getScene().getId();
    }
    
    // Show Form
    @RequestMapping(value = "/create")
    public String create(@RequestParam Integer sceneId, Model model) {

        CreateBlockViewModel viewModel = blockService.getCreateBlockViewModel(sceneId);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateBlockCommandModel());

        return "block/create";
    }

    // Handle Form Submission
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreateBlockCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreateBlockViewModel viewModel = blockService.getCreateBlockViewModel(commandModel.getSceneId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "block/create";
        }

        projectUndoRedoService.recordCheckpointForScene(commandModel.getSceneId());
        Block block = blockService.saveCreateBlockCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());

        return "redirect:/scene/show?id=" + block.getScene().getId();
    }
    
    // Show Form
    @RequestMapping(value = "/createBelow")
    public String createBelow(@RequestParam Integer id, Model model) {

        CreateBlockBelowViewModel viewModel = blockService.getCreateBlockBelowViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateBlockBelowCommandModel());

        return "block/createBelow";
    }

    // Handle Form Submission
    @RequestMapping(value = "/createBelow", method = RequestMethod.POST)
    public String saveCreateBelow(@Valid @ModelAttribute("commandModel") CreateBlockBelowCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreateBlockBelowViewModel viewModel = blockService.getCreateBlockBelowViewModel(commandModel.getSceneId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "block/createBelow";
        }

        projectUndoRedoService.recordCheckpointForBlock(commandModel.getId());
        Block block = blockService.saveCreateBlockBelowCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());

        return "redirect:/scene/show?id=" + block.getScene().getId();
    }

    @RequestMapping(value = "/createInline")
    public String createInline(@RequestParam Integer sceneId,
                               @RequestParam(required = false) String surface,
                               Model model) {
        CreateBlockViewModel viewModel = blockService.getCreateBlockViewModel(sceneId);
        model.addAttribute("viewModel", viewModel);
        if ("project".equals(surface)) {
            return "block/projectCreateInline";
        }
        return "block/createInline";
    }

    @RequestMapping(value = "/createInline", method = RequestMethod.POST)
    public String saveCreateInline(@RequestParam Integer sceneId,
                                   @RequestParam String content,
                                   @RequestParam(required = false) Integer personId,
                                   @RequestParam(required = false) String surface,
                                   Model model) {
        projectUndoRedoService.recordCheckpointForScene(sceneId);
        CreateBlockCommandModel commandModel = new CreateBlockCommandModel();
        commandModel.setSceneId(sceneId);
        commandModel.setContent(content);
        commandModel.setPersonId(personId);
        Block block = blockService.saveCreateBlockCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());
        BlockViewModel vm = blockService.getBlockViewModel(block.getId());
        model.addAttribute("block", vm);
        CreateBlockBelowViewModel createViewModel = blockService.getCreateBlockBelowViewModel(block.getId());
        model.addAttribute("viewModel", createViewModel);
        model.addAttribute("blockId", block.getId());
        model.addAttribute("projectId", block.getScene().getProject().getId());
        if ("project".equals(surface)) {
            return "block/projectBlockRowWithCreate";
        }
        return "block/blockRowWithCreate";
    }

    @RequestMapping(value = "/createBelowInline")
    public String createBelowInline(@RequestParam Integer id,
                                    @RequestParam(required = false) String surface,
                                    Model model) {
        CreateBlockBelowViewModel viewModel = blockService.getCreateBlockBelowViewModel(id);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("blockId", id);
        if ("project".equals(surface)) {
            return "block/projectCreateBelowInline";
        }
        return "block/createBelowInline";
    }

    @RequestMapping(value = "/createBelowInline", method = RequestMethod.POST)
    public String saveCreateBelowInline(@RequestParam Integer id,
                                        @RequestParam String content,
                                        @RequestParam(required = false) Integer personId,
                                        @RequestParam(required = false) String surface,
                                        Model model) {
        projectUndoRedoService.recordCheckpointForBlock(id);
        CreateBlockBelowCommandModel commandModel = new CreateBlockBelowCommandModel();
        commandModel.setId(id);
        commandModel.setContent(content);
        commandModel.setPersonId(personId);
        Block block = blockService.saveCreateBlockBelowCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());
        BlockViewModel vm = blockService.getBlockViewModel(block.getId());
        model.addAttribute("block", vm);
        CreateBlockBelowViewModel createViewModel = blockService.getCreateBlockBelowViewModel(block.getId());
        model.addAttribute("viewModel", createViewModel);
        model.addAttribute("blockId", block.getId());
        model.addAttribute("projectId", block.getScene().getProject().getId());
        if ("project".equals(surface)) {
            return "block/projectBlockRowWithCreate";
        }
        return "block/blockRowWithCreate";
    }

    @RequestMapping(value = "/bulkAddTags", method = RequestMethod.POST)
    public String bulkAddTags(@RequestParam String ids, @RequestParam String tags, @RequestParam(required = false) Integer sceneId, @RequestParam(required = false) Integer projectId) {
        if (ids != null && !ids.trim().isEmpty()) {
            java.util.List<Integer> blockIds = new java.util.ArrayList<>();
            for (String idStr : ids.split(",")) {
                try {
                    blockIds.add(Integer.parseInt(idStr.trim()));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            if (projectId != null) {
                projectUndoRedoService.recordCheckpoint(projectId);
            } else if (sceneId != null) {
                projectUndoRedoService.recordCheckpointForScene(sceneId);
            }
            blockService.addTagsToBlocks(blockIds, tags);
            if (projectId != null) {
                projectVersionService.autoSaveVersion(projectId);
            } else if (sceneId != null) {
                projectVersionService.autoSaveVersionForScene(sceneId);
            }
        }
        if (projectId != null) {
            return "redirect:/project/show?id=" + projectId;
        }
        return "redirect:/scene/show?id=" + sceneId;
    }

    @RequestMapping(value = "/bulkDelete", method = RequestMethod.POST)
    public String bulkDelete(@RequestParam String ids, @RequestParam(required = false) Integer sceneId, @RequestParam(required = false) Integer projectId) {
        if (ids != null && !ids.trim().isEmpty()) {
            java.util.List<Integer> blockIds = new java.util.ArrayList<>();
            for (String idStr : ids.split(",")) {
                try {
                    blockIds.add(Integer.parseInt(idStr.trim()));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            if (projectId != null) {
                projectUndoRedoService.recordCheckpoint(projectId);
            } else if (sceneId != null) {
                projectUndoRedoService.recordCheckpointForScene(sceneId);
            }
            blockService.deleteBlocks(blockIds);
            if (projectId != null) {
                projectVersionService.autoSaveVersion(projectId);
            } else if (sceneId != null) {
                projectVersionService.autoSaveVersionForScene(sceneId);
            }
        }
        if (projectId != null) {
            return "redirect:/project/show?id=" + projectId;
        }
        return "redirect:/scene/show?id=" + sceneId;
    }
}

