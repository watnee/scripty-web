package com.scripty.controller;

import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;
import com.scripty.commandmodel.user.edituser.EditUserCommandModel;
import com.scripty.dto.User;
import com.scripty.viewmodel.user.createuser.CreateUserViewModel;
import com.scripty.viewmodel.user.edituser.EditUserViewModel;
import com.scripty.viewmodel.user.userlist.UserListViewModel;
import com.scripty.viewmodel.user.userprofile.UserProfileViewModel;
import com.scripty.service.UserService;
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
@RequestMapping(value = "/user")
public class UserController {

    @Autowired
    UserService userService;

    @RequestMapping(value = "/list")
    public String list(Model model) {

        UserListViewModel viewModel = userService.getUserListViewModel();

        model.addAttribute("viewModel", viewModel);

        return "user/list";
    }

    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model) {

        UserProfileViewModel viewModel = userService.getUserProfileViewModel(id);

        model.addAttribute("viewModel", viewModel);

        return "user/show";
    }

    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {

        userService.deleteUser(id);

        return "redirect:/user/list";
    }

    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model) {

        EditUserViewModel viewModel = userService.getEditUserViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditUserCommandModel());

        return "user/edit";
    }

    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditUserCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            EditUserViewModel viewModel = userService.getEditUserViewModel(commandModel.getId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "user/edit";
        }

        userService.saveEditUserCommandModel(commandModel);

        return "redirect:/user/list";
    }

    @RequestMapping(value = "/create")
    public String create(Model model) {

        CreateUserViewModel viewModel = userService.getCreateUserViewModel();

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateUserCommandModel());

        return "user/create";
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreateUserCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreateUserViewModel viewModel = userService.getCreateUserViewModel();

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "user/create";
        }

        userService.saveCreateUserCommandModel(commandModel);

        return "redirect:/user/list";
    }
}
