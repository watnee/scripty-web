package com.scripty.controller;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.viewmodel.block.BlockViewModel;
import com.scripty.viewmodel.block.createblock.CreateBlockViewModel;
import com.scripty.viewmodel.block.createblockbelow.CreateBlockBelowViewModel;
import com.scripty.viewmodel.block.editblock.EditBlockViewModel;
import com.scripty.service.BlockService;
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

@Controller
@RequestMapping(value = "/block")
public class BlockController {

    @Autowired
    BlockService blockService;

    @Autowired
    ProjectVersionService projectVersionService;

    private String redirectToProject(Block block) {
        return "redirect:/project/show?id=" + block.getProject().getId();
    }

    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {

        Block block = blockService.deleteBlock(id);
        projectVersionService.autoSaveVersion(block.getProject().getId());

        return redirectToProject(block);
    }

    @RequestMapping(value = "/moveUp")
    public String moveUp(@RequestParam Integer id) {

        Block block = blockService.moveBlockUp(id);
        projectVersionService.autoSaveVersionForBlock(block.getId());

        return redirectToProject(block);
    }

    @RequestMapping(value = "/moveDown")
    public String moveDown(@RequestParam Integer id) {

        Block block = blockService.moveBlockDown(id);
        projectVersionService.autoSaveVersionForBlock(block.getId());

        return redirectToProject(block);
    }

    @RequestMapping(value = "/moveTo", method = RequestMethod.POST)
    public String moveTo(@RequestParam Integer id, @RequestParam int position) {
        Block block = blockService.moveBlockTo(id, position);
        projectVersionService.autoSaveVersionForBlock(block.getId());
        return redirectToProject(block);
    }

    @RequestMapping(value = "/toggleBookmark")
    public String toggleBookmark(@RequestParam Integer id) {
        Block block = blockService.toggleBookmark(id);
        return redirectToProject(block);
    }

    @RequestMapping(value = "/togglePinned")
    public String togglePinned(@RequestParam Integer id) {
        Block block = blockService.togglePinned(id);
        return redirectToProject(block);
    }

    @RequestMapping(value = "/editInline")
    public String editInline(@RequestParam Integer id, Model model) {
        EditBlockViewModel viewModel = blockService.getEditBlockViewModel(id);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditBlockCommandModel());
        return "block/editInline";
    }

    @RequestMapping(value = "/editInline", method = RequestMethod.POST)
    public String saveEditInline(@Valid @ModelAttribute("commandModel") EditBlockCommandModel commandModel, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            EditBlockViewModel viewModel = blockService.getEditBlockViewModel(commandModel.getId());
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);
            return "block/editInline";
        }
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

        Block block = blockService.saveEditBlockCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());

        return redirectToProject(block);
    }

    // Show Form
    @RequestMapping(value = "/create")
    public String create(@RequestParam Integer projectId, Model model) {

        CreateBlockViewModel viewModel = blockService.getCreateBlockViewModel(projectId);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateBlockCommandModel());

        return "block/create";
    }

    // Handle Form Submission
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreateBlockCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreateBlockViewModel viewModel = blockService.getCreateBlockViewModel(commandModel.getProjectId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "block/create";
        }

        Block block = blockService.saveCreateBlockCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());

        return redirectToProject(block);
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
            CreateBlockBelowViewModel viewModel = blockService.getCreateBlockBelowViewModel(commandModel.getId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "block/createBelow";
        }

        Block block = blockService.saveCreateBlockBelowCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());

        return redirectToProject(block);
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
        model.addAttribute("projectId", block.getProject().getId());
        if ("project".equals(surface)) {
            return "block/projectBlockRowWithCreate";
        }
        return "block/blockRowWithCreate";
    }

    // Creates a scene-type block at the end of the project.
    @RequestMapping(value = "/createSceneAndReturn", method = RequestMethod.POST)
    public String createSceneAndReturn(@RequestParam Integer projectId) {
        blockService.createSceneBlock(projectId, " ");
        projectVersionService.autoSaveVersion(projectId);
        return "redirect:/project/show?id=" + projectId;
    }

    @RequestMapping(value = "/editSceneNameInline")
    public String editSceneNameInline(@RequestParam Integer id, Model model) {
        BlockViewModel vm = blockService.getBlockViewModel(id);
        model.addAttribute("scene", vm);
        return "block/editSceneNameInline";
    }

    @RequestMapping(value = "/editSceneNameInline", method = RequestMethod.POST)
    public String saveEditSceneNameInline(@RequestParam Integer id, @RequestParam(defaultValue = "") String name, Model model) {
        Block block = blockService.updateSceneName(id, name);
        projectVersionService.autoSaveVersionForBlock(block.getId());
        BlockViewModel vm = blockService.getBlockViewModel(block.getId());
        model.addAttribute("scene", vm);
        return "block/showSceneNameInline";
    }

    @RequestMapping(value = "/bulkAddTags", method = RequestMethod.POST)
    public String bulkAddTags(@RequestParam String ids, @RequestParam String tags, @RequestParam Integer projectId) {
        if (ids != null && !ids.trim().isEmpty()) {
            java.util.List<Integer> blockIds = new java.util.ArrayList<>();
            for (String idStr : ids.split(",")) {
                try {
                    blockIds.add(Integer.parseInt(idStr.trim()));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            blockService.addTagsToBlocks(blockIds, tags);
            projectVersionService.autoSaveVersion(projectId);
        }
        return "redirect:/project/show?id=" + projectId;
    }

    @RequestMapping(value = "/bulkSetType", method = RequestMethod.POST)
    public String bulkSetType(@RequestParam String ids, @RequestParam String type, @RequestParam Integer projectId) {
        if (ids != null && !ids.trim().isEmpty()) {
            java.util.List<Integer> blockIds = new java.util.ArrayList<>();
            for (String idStr : ids.split(",")) {
                try {
                    blockIds.add(Integer.parseInt(idStr.trim()));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            blockService.setBlockTypes(blockIds, type);
            projectVersionService.autoSaveVersion(projectId);
        }
        return "redirect:/project/show?id=" + projectId;
    }

    @RequestMapping(value = "/bulkDelete", method = RequestMethod.POST)
    public String bulkDelete(@RequestParam String ids, @RequestParam Integer projectId) {
        if (ids != null && !ids.trim().isEmpty()) {
            java.util.List<Integer> blockIds = new java.util.ArrayList<>();
            for (String idStr : ids.split(",")) {
                try {
                    blockIds.add(Integer.parseInt(idStr.trim()));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            blockService.deleteBlocks(blockIds);
            projectVersionService.autoSaveVersion(projectId);
        }
        return "redirect:/project/show?id=" + projectId;
    }
}
