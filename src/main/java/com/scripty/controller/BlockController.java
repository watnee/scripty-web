package com.scripty.controller;

import com.scripty.api.HypermediaSupport;
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
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;

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

    private String resolveProjectSurface(String surface, HttpServletRequest request) {
        if ("project".equals(surface)) {
            return "project";
        }
        if (request != null) {
            String referer = request.getHeader("Referer");
            if (referer != null && referer.contains("/project/show")) {
                return "project";
            }
        }
        return surface;
    }

    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {

        Block block = blockService.deleteBlock(id);
        projectVersionService.autoSaveVersion(block.getProject().getId());

        return redirectToProject(block);
    }

    @RequestMapping(value = "/deleteInline", method = RequestMethod.POST)
    @org.springframework.web.bind.annotation.ResponseBody
    public String deleteInline(@RequestParam Integer id) {

        Block block = blockService.deleteBlock(id);
        projectVersionService.autoSaveVersion(block.getProject().getId());

        return "";
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

    @RequestMapping(value = "/toggleBookmarkInline", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    @org.springframework.web.bind.annotation.ResponseBody
    public EntityModel<java.util.Map<String, Boolean>> toggleBookmarkInline(@RequestParam Integer id) {
        Block block = blockService.toggleBookmark(id);
        return HypermediaSupport.blockToggle(java.util.Map.of("bookmarked", block.isBookmarked()), id, true);
    }

    @RequestMapping(value = "/togglePinned")
    public String togglePinned(@RequestParam Integer id) {
        Block block = blockService.togglePinned(id);
        return redirectToProject(block);
    }

    @RequestMapping(value = "/togglePinnedInline", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    @org.springframework.web.bind.annotation.ResponseBody
    public EntityModel<java.util.Map<String, Boolean>> togglePinnedInline(@RequestParam Integer id) {
        Block block = blockService.togglePinned(id);
        return HypermediaSupport.blockToggle(java.util.Map.of("pinned", block.isPinned()), id, false);
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

    @RequestMapping(value = "/createInline")
    public String createInline(@RequestParam Integer projectId,
                               @RequestParam(required = false) String surface,
                               HttpServletRequest request,
                               Model model) {
        surface = resolveProjectSurface(surface, request);
        CreateBlockViewModel viewModel = blockService.getCreateBlockViewModel(projectId);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("projectId", projectId);
        if ("project".equals(surface)) {
            return "block/projectCreateInline";
        }
        return "block/create";
    }

    @RequestMapping(value = "/createInline", method = RequestMethod.POST)
    public String saveCreateInline(@RequestParam Integer projectId,
                                   @RequestParam(defaultValue = "") String content,
                                   @RequestParam(required = false) Integer personId,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) String surface,
                                   HttpServletRequest request,
                                   Model model) {
        surface = resolveProjectSurface(surface, request);
        if (!"project".equals(surface) && (content == null || content.trim().isEmpty())) {
            CreateBlockViewModel viewModel = blockService.getCreateBlockViewModel(projectId);
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("projectId", projectId);
            return "block/create";
        }

        CreateBlockCommandModel commandModel = new CreateBlockCommandModel();
        commandModel.setProjectId(projectId);
        commandModel.setContent(content);
        commandModel.setPersonId(personId);
        commandModel.setType(type);
        Block block = blockService.saveCreateBlockCommandModel(commandModel);
        projectVersionService.autoSaveVersionForBlock(block.getId());

        BlockViewModel vm = blockService.getBlockViewModel(block.getId());
        CreateBlockBelowViewModel createViewModel = blockService.getCreateBlockBelowViewModel(block.getId());
        model.addAttribute("block", vm);
        model.addAttribute("viewModel", createViewModel);
        model.addAttribute("blockId", block.getId());
        model.addAttribute("projectId", projectId);
        model.addAttribute("canMoveUp", false);
        model.addAttribute("canMoveDown", false);
        if ("project".equals(surface)) {
            return "block/projectBlockRowWithCreate";
        }
        return "block/blockRowWithCreate";
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
                                    HttpServletRequest request,
                                    Model model) {
        surface = resolveProjectSurface(surface, request);
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
                                        @RequestParam(defaultValue = "") String content,
                                        @RequestParam(required = false) Integer personId,
                                        @RequestParam(required = false) String type,
                                        @RequestParam(required = false) String surface,
                                        HttpServletRequest request,
                                        Model model) {
        surface = resolveProjectSurface(surface, request);
        if (!"project".equals(surface) && (content == null || content.trim().isEmpty())) {
            model.addAttribute("blockId", id);
            return "block/createBelowInline";
        }

        CreateBlockBelowCommandModel commandModel = new CreateBlockBelowCommandModel();
        commandModel.setId(id);
        commandModel.setContent(content);
        commandModel.setPersonId(personId);
        commandModel.setType(type);
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

    @RequestMapping(value = "/editCharacterNameInline")
    public String editCharacterNameInline(@RequestParam Integer id, Model model) {
        BlockViewModel vm = blockService.getBlockViewModel(id);
        model.addAttribute("block", vm);
        return "block/editCharacterNameInline";
    }

    @RequestMapping(value = "/editCharacterNameInline", method = RequestMethod.POST)
    public String saveEditCharacterNameInline(@RequestParam Integer id, @RequestParam(defaultValue = "") String name, Model model) {
        Block block = blockService.updateCharacterName(id, name);
        projectVersionService.autoSaveVersionForBlock(block.getId());
        BlockViewModel vm = blockService.getBlockViewModel(block.getId());
        model.addAttribute("block", vm);
        model.addAttribute("updateInlinePersonInput", true);
        return "block/showCharacterNameInline";
    }

    private java.util.List<Integer> parseBlockIds(String ids) {
        java.util.List<Integer> blockIds = new java.util.ArrayList<>();
        if (ids == null || ids.trim().isEmpty()) {
            return blockIds;
        }
        for (String idStr : ids.split(",")) {
            try {
                blockIds.add(Integer.parseInt(idStr.trim()));
            } catch (NumberFormatException e) {
                // Ignore invalid ids (e.g. select-all checkbox value "on")
            }
        }
        return blockIds;
    }

    private Integer resolveProjectId(Integer projectId, java.util.List<Integer> blockIds) {
        if (projectId != null) {
            return projectId;
        }
        if (blockIds == null || blockIds.isEmpty()) {
            return null;
        }
        Block block = blockService.read(blockIds.get(0));
        return block != null ? block.getProject().getId() : null;
    }

    private String redirectAfterBulkAction(Integer projectId, java.util.List<Integer> blockIds) {
        Integer resolvedProjectId = resolveProjectId(projectId, blockIds);
        if (resolvedProjectId == null) {
            return "redirect:/project/list";
        }
        return "redirect:/project/show?id=" + resolvedProjectId;
    }

    @RequestMapping(value = "/bulkAddTags", method = RequestMethod.POST)
    public String bulkAddTags(@RequestParam String ids, @RequestParam String tags,
                              @RequestParam(required = false) Integer projectId) {
        java.util.List<Integer> blockIds = parseBlockIds(ids);
        if (!blockIds.isEmpty()) {
            blockService.addTagsToBlocks(blockIds, tags);
            Integer resolvedProjectId = resolveProjectId(projectId, blockIds);
            if (resolvedProjectId != null) {
                projectVersionService.autoSaveVersion(resolvedProjectId);
            }
        }
        return redirectAfterBulkAction(projectId, blockIds);
    }

    @RequestMapping(value = "/bulkSetType", method = RequestMethod.POST)
    public String bulkSetType(@RequestParam String ids, @RequestParam String type,
                              @RequestParam(required = false) Integer projectId) {
        java.util.List<Integer> blockIds = parseBlockIds(ids);
        if (!blockIds.isEmpty()) {
            blockService.setBlockTypes(blockIds, type);
            Integer resolvedProjectId = resolveProjectId(projectId, blockIds);
            if (resolvedProjectId != null) {
                projectVersionService.autoSaveVersion(resolvedProjectId);
            }
        }
        return redirectAfterBulkAction(projectId, blockIds);
    }

    @RequestMapping(value = "/setTypeAndContent", method = RequestMethod.POST)
    public String setTypeAndContent(@RequestParam Integer id,
                                    @RequestParam String type,
                                    @RequestParam(required = false) String content,
                                    @RequestParam(required = false) Integer personId,
                                    @RequestParam(required = false) String tags,
                                    @RequestParam(required = false) Integer projectId,
                                    @RequestParam(required = false) String partial,
                                    Model model) {
        Block block = blockService.updateBlockTypeAndContent(id, type, content, personId, tags);
        if (block != null) {
            projectVersionService.autoSaveVersionForBlock(block.getId());
        }
        if ("project".equals(partial) && block != null) {
            EditBlockViewModel viewModel = blockService.getEditBlockViewModel(id);
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", viewModel.getEditBlockCommandModel());
            model.addAttribute("block", blockService.getBlockViewModel(id));
            return "block/editInline";
        }
        return redirectAfterBulkAction(projectId, java.util.List.of(id));
    }

    @RequestMapping(value = "/bulkSetAlign", method = RequestMethod.POST)
    public String bulkSetAlign(@RequestParam String ids, @RequestParam String align,
                               @RequestParam(required = false) Integer projectId) {
        java.util.List<Integer> blockIds = parseBlockIds(ids);
        if (!blockIds.isEmpty()) {
            blockService.setBlockAlignments(blockIds, align);
            Integer resolvedProjectId = resolveProjectId(projectId, blockIds);
            if (resolvedProjectId != null) {
                projectVersionService.autoSaveVersion(resolvedProjectId);
            }
        }
        return redirectAfterBulkAction(projectId, blockIds);
    }

    @RequestMapping(value = "/bulkToggleStyle", method = RequestMethod.POST)
    public String bulkToggleStyle(@RequestParam String ids, @RequestParam String style,
                                  @RequestParam(required = false) Integer projectId) {
        java.util.List<Integer> blockIds = parseBlockIds(ids);
        if (!blockIds.isEmpty()) {
            blockService.toggleBlockTextStyles(blockIds, style);
            Integer resolvedProjectId = resolveProjectId(projectId, blockIds);
            if (resolvedProjectId != null) {
                projectVersionService.autoSaveVersion(resolvedProjectId);
            }
        }
        return redirectAfterBulkAction(projectId, blockIds);
    }

    @RequestMapping(value = "/bulkDelete", method = RequestMethod.POST)
    public String bulkDelete(@RequestParam String ids, @RequestParam(required = false) Integer projectId) {
        java.util.List<Integer> blockIds = parseBlockIds(ids);
        if (!blockIds.isEmpty()) {
            blockService.deleteBlocks(blockIds);
            Integer resolvedProjectId = resolveProjectId(projectId, blockIds);
            if (resolvedProjectId != null) {
                projectVersionService.autoSaveVersion(resolvedProjectId);
            }
        }
        return redirectAfterBulkAction(projectId, blockIds);
    }
}
