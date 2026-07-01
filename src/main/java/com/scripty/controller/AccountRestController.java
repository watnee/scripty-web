package com.scripty.controller;

import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;
import com.scripty.commandmodel.user.edituser.EditUserCommandModel;
import com.scripty.dto.User;
import com.scripty.viewmodel.user.accountprofile.AccountProfileViewModel;
import com.scripty.viewmodel.user.userlist.UserListViewModel;
import com.scripty.service.UserService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/account")
public class AccountRestController {

    @Autowired
    UserService userService;

    @RequestMapping(method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<?> list() {
        UserListViewModel viewModel = userService.getUserListViewModel();
        return new ResponseEntity<>(viewModel.getUsers(), HttpStatus.OK);
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> create(@Valid @RequestBody CreateUserCommandModel commandModel, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(buildErrors(bindingResult), HttpStatus.BAD_REQUEST);
        }
        User user = userService.saveCreateUserCommandModel(commandModel);
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());
        response.put("team", user.getTeam());
        response.put("admin", user.isAdmin());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<?> show(@PathVariable Integer id) {
        AccountProfileViewModel viewModel = userService.getAccountProfileViewModel(id);
        Map<String, Object> response = new HashMap<>();
        response.put("id", viewModel.getId());
        response.put("username", viewModel.getUsername());
        response.put("firstName", viewModel.getFirstName());
        response.put("lastName", viewModel.getLastName());
        response.put("team", viewModel.getTeam());
        response.put("admin", viewModel.isAdmin());
        response.put("enabled", viewModel.isEnabled());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> update(@PathVariable Integer id, @Valid @RequestBody EditUserCommandModel commandModel, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(buildErrors(bindingResult), HttpStatus.BAD_REQUEST);
        }
        commandModel.setId(id);
        User user = userService.saveEditUserCommandModel(commandModel);
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());
        response.put("team", user.getTeam());
        response.put("admin", user.isAdmin());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = "application/json")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        User user = userService.deleteUser(id);
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private Map<String, String> buildErrors(BindingResult bindingResult) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }
}
