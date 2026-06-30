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
import com.scripty.service.PersonService;
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
@RequestMapping(value = "/character")
public class PersonController {
    
    @Autowired
    PersonService personService;
    
    @RequestMapping(value = "/list")
    public String list(@RequestParam Integer projectId, Model model) {

        PersonListViewModel viewModel = personService.getPersonListViewModel(projectId);

        model.addAttribute("viewModel", viewModel);

        return "character/list";
    }

    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model) {

        PersonProfileViewModel viewModel = personService.getPersonProfileViewModel(id);

        model.addAttribute("viewModel", viewModel);

        return "character/show";
    }
    
    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {
        
        Person person = personService.deletePerson(id);
        
        return "redirect:/project/show?id=" + person.getProject().getId();
    }
    
    // Show Form
    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model) {

        EditPersonViewModel viewModel = personService.getEditPersonViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditPersonCommandModel());

        return "character/edit";
    }

    // Handle Form Submission
    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditPersonCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            EditPersonViewModel viewModel = personService.getEditPersonViewModel(commandModel.getId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "character/edit";
        }

        Person person = personService.saveEditPersonCommandModel(commandModel);

        return "redirect:/character/show?id=" + person.getId();
    }
    
    // Show Form
    @RequestMapping(value = "/create")
    public String create(@RequestParam Integer projectId, Model model) {

        CreatePersonViewModel viewModel = personService.getCreatePersonViewModel(projectId);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreatePersonCommandModel());

        return "character/create";
    }

    // Handle Form Submission
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreatePersonCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreatePersonViewModel viewModel = personService.getCreatePersonViewModel(commandModel.getProjectId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "character/create";
        }

        Person person = personService.saveCreatePersonCommandModel(commandModel);

        return "redirect:/character/show?id=" + person.getId();
    }
}
