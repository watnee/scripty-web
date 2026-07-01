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
    
    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {
        
        Block block = blockService.deleteBlock(id);
        
        return "redirect:/scene/show?id=" + block.getScene().getId();
    }
    
    @RequestMapping(value = "/moveUp")
    public String moveUp(@RequestParam Integer id) {
        
        Block block = blockService.moveBlockUp(id);
        
        return "redirect:/scene/show?id=" + block.getScene().getId();
    }
    
    @RequestMapping(value = "/moveDown")
    public String moveDown(@RequestParam Integer id) {
        
        Block block = blockService.moveBlockDown(id);
        
        return "redirect:/scene/show?id=" + block.getScene().getId();
    }
    
    @RequestMapping(value = "/moveTo", method = RequestMethod.POST)
    public String moveTo(@RequestParam Integer id, @RequestParam int position) {
        Block block = blockService.moveBlockTo(id, position);
        return "redirect:/scene/show?id=" + block.getScene().getId();
    }

    @RequestMapping(value = "/toggleBookmark")
    public String toggleBookmark(@RequestParam Integer id) {
        Block block = blockService.toggleBookmark(id);
        return "redirect:/scene/show?id=" + block.getScene().getId();
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

        Block block = blockService.saveCreateBlockCommandModel(commandModel);

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

        Block block = blockService.saveCreateBlockBelowCommandModel(commandModel);

        return "redirect:/scene/show?id=" + block.getScene().getId();
    }

    @RequestMapping(value = "/createInline")
    public String createInline(@RequestParam Integer sceneId, Model model) {
        CreateBlockViewModel viewModel = blockService.getCreateBlockViewModel(sceneId);
        model.addAttribute("viewModel", viewModel);
        return "block/createInline";
    }

    @RequestMapping(value = "/createInline", method = RequestMethod.POST)
    public String saveCreateInline(@RequestParam Integer sceneId, @RequestParam String content, @RequestParam(required = false) Integer personId, Model model) {
        CreateBlockCommandModel commandModel = new CreateBlockCommandModel();
        commandModel.setSceneId(sceneId);
        commandModel.setContent(content);
        commandModel.setPersonId(personId);
        Block block = blockService.saveCreateBlockCommandModel(commandModel);
        BlockViewModel vm = blockService.getBlockViewModel(block.getId());
        model.addAttribute("block", vm);
        CreateBlockBelowViewModel createViewModel = blockService.getCreateBlockBelowViewModel(block.getId());
        model.addAttribute("viewModel", createViewModel);
        model.addAttribute("blockId", block.getId());
        return "block/blockRowWithCreate";
    }

    @RequestMapping(value = "/createBelowInline")
    public String createBelowInline(@RequestParam Integer id, Model model) {
        CreateBlockBelowViewModel viewModel = blockService.getCreateBlockBelowViewModel(id);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("blockId", id);
        return "block/createBelowInline";
    }

    @RequestMapping(value = "/createBelowInline", method = RequestMethod.POST)
    public String saveCreateBelowInline(@RequestParam Integer id, @RequestParam String content, @RequestParam(required = false) Integer personId, Model model) {
        CreateBlockBelowCommandModel commandModel = new CreateBlockBelowCommandModel();
        commandModel.setId(id);
        commandModel.setContent(content);
        commandModel.setPersonId(personId);
        Block block = blockService.saveCreateBlockBelowCommandModel(commandModel);
        BlockViewModel vm = blockService.getBlockViewModel(block.getId());
        model.addAttribute("block", vm);
        CreateBlockBelowViewModel createViewModel = blockService.getCreateBlockBelowViewModel(block.getId());
        model.addAttribute("viewModel", createViewModel);
        model.addAttribute("blockId", block.getId());
        return "block/blockRowWithCreate";
    }
}
