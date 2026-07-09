package com.scripty.controller;

import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import com.scripty.commandmodel.person.editperson.EditPersonCommandModel;
import com.scripty.dto.Person;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.PersonService;
import com.scripty.service.ProjectVersionService;
import com.scripty.viewmodel.person.createperson.CreatePersonViewModel;
import com.scripty.viewmodel.person.editperson.EditPersonViewModel;
import com.scripty.viewmodel.person.personlist.PersonListViewModel;
import com.scripty.viewmodel.person.personprofile.PersonProfileViewModel;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/character")
public class PersonController {

    @Autowired
    PersonService personService;

    @Autowired
    ProjectVersionService projectVersionService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(value = "/list")
    public String list(@RequestParam Integer projectId, Model model, Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return "redirect:/project/list";
        }

        PersonListViewModel viewModel = personService.getPersonListViewModel(projectId);

        model.addAttribute("viewModel", viewModel);

        return "character/list";
    }

    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model, Principal principal) {
        if (!projectAccess.canAccessPerson(id, principal)) {
            return "redirect:/project/list";
        }

        PersonProfileViewModel viewModel = personService.getPersonProfileViewModel(id);

        model.addAttribute("viewModel", viewModel);

        return "character/show";
    }

    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id, Principal principal) {
        if (!projectAccess.canAccessPerson(id, principal)) {
            return "redirect:/project/list";
        }

        Person person = personService.deletePerson(id);
        projectVersionService.autoSaveVersionForPerson(person.getId());

        return "redirect:/project/show?id=" + person.getProject().getId();
    }

    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model, Principal principal) {
        if (!projectAccess.canAccessPerson(id, principal)) {
            return "redirect:/project/list";
        }

        EditPersonViewModel viewModel = personService.getEditPersonViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditPersonCommandModel());

        return "character/edit";
    }

    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditPersonCommandModel commandModel,
                           BindingResult bindingResult,
                           Model model,
                           Principal principal) {
        if (!projectAccess.canAccessPerson(commandModel.getId(), principal)) {
            return "redirect:/project/list";
        }

        if (bindingResult.hasErrors()) {
            EditPersonViewModel viewModel = personService.getEditPersonViewModel(commandModel.getId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "character/edit";
        }

        Person person = personService.saveEditPersonCommandModel(commandModel);
        projectVersionService.autoSaveVersionForPerson(person.getId());

        return "redirect:/character/show?id=" + person.getId();
    }

    @RequestMapping(value = "/create")
    public String create(@RequestParam Integer projectId, Model model, Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return "redirect:/project/list";
        }

        CreatePersonViewModel viewModel = personService.getCreatePersonViewModel(projectId);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreatePersonCommandModel());

        return "character/create";
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreatePersonCommandModel commandModel,
                             BindingResult bindingResult,
                             Model model,
                             Principal principal) {
        if (!projectAccess.canAccessProject(commandModel.getProjectId(), principal)) {
            return "redirect:/project/list";
        }

        if (bindingResult.hasErrors()) {
            CreatePersonViewModel viewModel = personService.getCreatePersonViewModel(commandModel.getProjectId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "character/create";
        }

        Person person = personService.saveCreatePersonCommandModel(commandModel);
        projectVersionService.autoSaveVersionForPerson(person.getId());

        return "redirect:/character/show?id=" + person.getId();
    }

    @RequestMapping(value = "/assignActor", method = RequestMethod.POST)
    public String assignActor(@RequestParam Integer characterId,
                              @RequestParam(required = false) Integer actorId,
                              Principal principal) {
        if (!projectAccess.canAccessPerson(characterId, principal)) {
            return "redirect:/project/list";
        }

        Person person = personService.assignActorToCharacter(characterId, actorId);
        projectVersionService.autoSaveVersionForPerson(person.getId());

        return "redirect:/actor/list?projectId=" + person.getProject().getId();
    }
}
