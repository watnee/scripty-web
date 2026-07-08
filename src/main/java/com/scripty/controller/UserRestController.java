package com.scripty.controller;

import com.scripty.api.UserResource;
import com.scripty.api.UserResourceAssembler;
import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;
import com.scripty.commandmodel.user.edituser.EditUserCommandModel;
import com.scripty.dto.User;
import com.scripty.viewmodel.user.userlist.UserListViewModel;
import com.scripty.viewmodel.user.userprofile.UserProfileViewModel;
import com.scripty.service.UserService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
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
@RequestMapping(value = "/api/user")
public class UserRestController {

    @Autowired
    UserService userService;

    @Autowired
    UserResourceAssembler userResourceAssembler;

    @RequestMapping(method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<CollectionModel<EntityModel<UserResource>>> list() {
        UserListViewModel viewModel = userService.getUserListViewModel();
        return ResponseEntity.ok(userResourceAssembler.toUserCollection(viewModel.getUsers()));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<UserResource>> create(
            @Valid @RequestBody CreateUserCommandModel commandModel, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(buildErrors(bindingResult), HttpStatus.BAD_REQUEST);
        }
        User user = userService.saveCreateUserCommandModel(commandModel);
        EntityModel<UserResource> resource = userResourceAssembler.toModel(user);
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<UserResource>> show(@PathVariable Integer id) {
        UserProfileViewModel viewModel = userService.getUserProfileViewModel(id);
        return ResponseEntity.ok(userResourceAssembler.toModel(viewModel));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @Valid @RequestBody EditUserCommandModel commandModel,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(buildErrors(bindingResult), HttpStatus.BAD_REQUEST);
        }
        commandModel.setId(id);
        User user = userService.saveEditUserCommandModel(commandModel);
        return ResponseEntity.ok(userResourceAssembler.toModel(user));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<UserResource>> delete(@PathVariable Integer id) {
        User user = userService.deleteUser(id);
        return ResponseEntity.ok(userResourceAssembler.toDeleteModel(user));
    }

    private Map<String, String> buildErrors(BindingResult bindingResult) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }
}
