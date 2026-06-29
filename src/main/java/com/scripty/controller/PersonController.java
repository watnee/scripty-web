/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.controller;

import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import com.scripty.commandmodel.person.editperson.EditPersonCommandModel;
import com.scripty.dto.Person;
import com.scripty.viewmodel.person.createperson.CreatePersonViewModel;
import com.scripty.viewmodel.person.editperson.EditPersonViewModel;
import com.scripty.viewmodel.person.personlist.PersonListViewModel;
import com.scripty.viewmodel.person.personprofile.PersonProfileViewModel;
import com.scripty.webservice.PersonWebService;
import javax.inject.Inject;
import javax.validation.Valid;
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
@RequestMapping(value = "/character")
public class PersonController {
    
    @Inject
    PersonWebService personWebService;
    
    @RequestMapping(value = "/list")
    public String list(@RequestParam Integer projectId, Model model) {

        PersonListViewModel viewModel = personWebService.getPersonListViewModel(projectId);

        model.addAttribute("viewModel", viewModel);

        return "character/list";
    }

    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model) {

        PersonProfileViewModel viewModel = personWebService.getPersonProfileViewModel(id);

        model.addAttribute("viewModel", viewModel);

        return "character/show";
    }
    
    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {
        
        Person person = personWebService.deletePerson(id);
        
        return "redirect:/project/show?id=" + person.getProject().getId();
    }
    
    // Show Form
    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model) {

        EditPersonViewModel viewModel = personWebService.getEditPersonViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditPersonCommandModel());

        return "character/edit";
    }

    // Handle Form Submission
    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditPersonCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            EditPersonViewModel viewModel = personWebService.getEditPersonViewModel(commandModel.getId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "character/edit";
        }

        Person person = personWebService.saveEditPersonCommandModel(commandModel);

        return "redirect:/character/show?id=" + person.getId();
    }
    
    // Show Form
    @RequestMapping(value = "/create")
    public String create(@RequestParam Integer projectId, Model model) {

        CreatePersonViewModel viewModel = personWebService.getCreatePersonViewModel(projectId);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreatePersonCommandModel());

        return "character/create";
    }

    // Handle Form Submission
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreatePersonCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreatePersonViewModel viewModel = personWebService.getCreatePersonViewModel(commandModel.getProjectId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "character/create";
        }

        Person person = personWebService.saveCreatePersonCommandModel(commandModel);

        return "redirect:/character/show?id=" + person.getId();
    }
}
