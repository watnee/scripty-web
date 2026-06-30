/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.controller;

import com.scripty.commandmodel.scene.createscene.CreateSceneCommandModel;
import com.scripty.commandmodel.scene.createscenebelow.CreateSceneBelowCommandModel;
import com.scripty.commandmodel.scene.editscene.EditSceneCommandModel;
import com.scripty.dto.Scene;
import com.scripty.viewmodel.scene.createscene.CreateSceneViewModel;
import com.scripty.viewmodel.scene.createscenebelow.CreateSceneBelowViewModel;
import com.scripty.viewmodel.scene.editscene.EditSceneViewModel;
import com.scripty.viewmodel.scene.allscenes.AllScenesViewModel;
import com.scripty.viewmodel.scene.sceneprofile.SceneProfileViewModel;
import com.scripty.service.SceneService;
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
@RequestMapping(value = "/scene")
public class SceneController {
    
    @Autowired
    SceneService sceneService;
    
    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model) {

        SceneProfileViewModel viewModel = sceneService.getSceneProfileViewModel(id);

        model.addAttribute("viewModel", viewModel);

        return "scene/show";
    }

    @RequestMapping(value = "/all")
    public String all(@RequestParam Integer projectId, Model model) {

        AllScenesViewModel viewModel = sceneService.getAllScenesViewModel(projectId);

        model.addAttribute("viewModel", viewModel);

        return "scene/all";
    }
    
    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {
        
        Scene scene = sceneService.deleteScene(id);
        
        return "redirect:/project/show?id=" + scene.getProject().getId();
    }
    
    @RequestMapping(value = "/moveUp")
    public String moveUp(@RequestParam Integer id) {
        
        Scene scene = sceneService.moveSceneUp(id);
        
        return "redirect:/project/show?id=" + scene.getProject().getId();
    }
    
    @RequestMapping(value = "/moveDown")
    public String moveDown(@RequestParam Integer id) {
        
        Scene scene = sceneService.moveSceneDown(id);
        
        return "redirect:/project/show?id=" + scene.getProject().getId();
    }
    
    // Show Form
    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model) {

        EditSceneViewModel viewModel = sceneService.getEditSceneViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditSceneCommandModel());

        return "scene/edit";
    }

    // Handle Form Submission
    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditSceneCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            EditSceneViewModel viewModel = sceneService.getEditSceneViewModel(commandModel.getId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "scene/edit";
        }

        Scene scene = sceneService.saveEditSceneCommandModel(commandModel);

        return "redirect:/scene/show?id=" + scene.getId();
    }
    
    // Show Form
    @RequestMapping(value = "/create")
    public String create(@RequestParam Integer projectId, Model model) {

        CreateSceneViewModel viewModel = sceneService.getCreateSceneViewModel(projectId);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateSceneCommandModel());

        return "scene/create";
    }

    // Handle Form Submission
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreateSceneCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreateSceneViewModel viewModel = sceneService.getCreateSceneViewModel(commandModel.getProjectId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "scene/create";
        }

        Scene scene = sceneService.saveCreateSceneCommandModel(commandModel);

        return "redirect:/scene/show?id=" + scene.getId();
    }
    
    // Show Form
    @RequestMapping(value = "/createBelow")
    public String createBelow(@RequestParam Integer id, Model model) {

        CreateSceneBelowViewModel viewModel = sceneService.getCreateSceneBelowViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateSceneBelowCommandModel());

        return "scene/createBelow";
    }

    // Handle Form Submission
    @RequestMapping(value = "/createBelow", method = RequestMethod.POST)
    public String saveCreateBelow(@Valid @ModelAttribute("commandModel") CreateSceneBelowCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreateSceneBelowViewModel viewModel = sceneService.getCreateSceneBelowViewModel(commandModel.getProjectId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "scene/createBelow";
        }

        Scene scene = sceneService.saveCreateSceneBelowCommandModel(commandModel);

        return "redirect:/scene/show?id=" + scene.getId();
    }

    @RequestMapping(value = "/createInline")
    public String createInline(@RequestParam Integer projectId, Model model) {
        model.addAttribute("projectId", projectId);
        return "scene/createInline";
    }

    @RequestMapping(value = "/createInline", method = RequestMethod.POST)
    public String saveCreateInline(@Valid @ModelAttribute("commandModel") CreateSceneCommandModel commandModel, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("projectId", commandModel.getProjectId());
            return "scene/createInline";
        }
        Scene scene = sceneService.saveCreateSceneCommandModel(commandModel);
        model.addAttribute("scene", scene);
        return "scene/sceneRow";
    }

    @RequestMapping(value = "/editNameInline")
    public String editNameInline(@RequestParam Integer id, Model model) {
        EditSceneViewModel viewModel = sceneService.getEditSceneViewModel(id);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditSceneCommandModel());
        return "scene/editNameInline";
    }

    @RequestMapping(value = "/editNameInline", method = RequestMethod.POST)
    public String saveEditNameInline(@Valid @ModelAttribute("commandModel") EditSceneCommandModel commandModel, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            EditSceneViewModel viewModel = sceneService.getEditSceneViewModel(commandModel.getId());
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);
            return "scene/editNameInline";
        }
        Scene scene = sceneService.saveEditSceneCommandModel(commandModel);
        model.addAttribute("scene", scene);
        return "scene/showNameInline";
    }

    @RequestMapping(value = "/createBelowInline")
    public String createBelowInline(@RequestParam Integer id, Model model) {
        model.addAttribute("sceneId", id);
        return "scene/createBelowInline";
    }

    @RequestMapping(value = "/createBelowInline", method = RequestMethod.POST)
    public String saveCreateBelowInline(@Valid @ModelAttribute("commandModel") CreateSceneBelowCommandModel commandModel, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("sceneId", commandModel.getId());
            return "scene/createBelowInline";
        }
        Scene scene = sceneService.saveCreateSceneBelowCommandModel(commandModel);
        return "redirect:/scene/show?id=" + scene.getId();
    }

    @RequestMapping(value = "/createAndReturn", method = RequestMethod.POST)
    public String createAndReturn(@RequestParam Integer projectId) {
        CreateSceneCommandModel commandModel = new CreateSceneCommandModel();
        commandModel.setProjectId(projectId);
        commandModel.setName(" ");
        sceneService.saveCreateSceneCommandModel(commandModel);
        return "redirect:/project/show?id=" + projectId;
    }
}
