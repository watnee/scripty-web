package com.scripty.webservice;

import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;
import com.scripty.commandmodel.user.edituser.EditUserCommandModel;
import com.scripty.dto.User;
import com.scripty.service.UserService;
import com.scripty.viewmodel.user.createuser.CreateUserViewModel;
import com.scripty.viewmodel.user.edituser.EditUserViewModel;
import com.scripty.viewmodel.user.userlist.UserListViewModel;
import com.scripty.viewmodel.user.userlist.UserViewModel;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class UserWebServiceImpl implements UserWebService {

    UserService userService;

    @Inject
    public UserWebServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserListViewModel getUserListViewModel() {

        UserListViewModel userListViewModel = new UserListViewModel();

        List<User> users = userService.list();

        userListViewModel.setUsers(translate(users));

        return userListViewModel;
    }

    @Override
    public CreateUserViewModel getCreateUserViewModel() {

        CreateUserViewModel createUserViewModel = new CreateUserViewModel();

        CreateUserCommandModel commandModel = new CreateUserCommandModel();
        createUserViewModel.setCreateUserCommandModel(commandModel);

        return createUserViewModel;
    }

    @Override
    public EditUserViewModel getEditUserViewModel(Integer id) {

        EditUserViewModel editUserViewModel = new EditUserViewModel();

        User existingUser = userService.read(id);

        editUserViewModel.setId(id);

        EditUserCommandModel commandModel = new EditUserCommandModel();
        commandModel.setId(existingUser.getId());
        commandModel.setUsername(existingUser.getUsername());
        commandModel.setFirstName(existingUser.getFirstName());
        commandModel.setLastName(existingUser.getLastName());
        commandModel.setAdmin(existingUser.isAdmin());

        editUserViewModel.setEditUserCommandModel(commandModel);

        return editUserViewModel;
    }

    @Override
    public User saveCreateUserCommandModel(CreateUserCommandModel createUserCommandModel) {

        User user = new User();

        user.setUsername(createUserCommandModel.getUsername());
        user.setPassword(createUserCommandModel.getPassword());
        user.setFirstName(createUserCommandModel.getFirstName());
        user.setLastName(createUserCommandModel.getLastName());
        user.setEnabled(true);
        user.setAdmin(createUserCommandModel.isAdmin());

        user = userService.create(user);

        return user;
    }

    @Override
    public User saveEditUserCommandModel(EditUserCommandModel editUserCommandModel) {

        User user = userService.read(editUserCommandModel.getId());

        user.setUsername(editUserCommandModel.getUsername());
        user.setPassword(editUserCommandModel.getPassword());
        user.setFirstName(editUserCommandModel.getFirstName());
        user.setLastName(editUserCommandModel.getLastName());
        user.setAdmin(editUserCommandModel.isAdmin());

        userService.update(user);

        return user;
    }

    @Override
    public User deleteUser(Integer id) {

        User user = userService.read(id);

        userService.delete(user);

        return user;
    }

    private List<UserViewModel> translate(List<User> users) {
        List<UserViewModel> userViewModels = new ArrayList<>();

        for (User user : users) {
            userViewModels.add(translate(user));
        }

        return userViewModels;
    }

    private UserViewModel translate(User user) {

        UserViewModel userViewModel = new UserViewModel();

        userViewModel.setId(user.getId());
        userViewModel.setUsername(user.getUsername());
        userViewModel.setFirstName(user.getFirstName());
        userViewModel.setLastName(user.getLastName());
        userViewModel.setEnabled(user.isEnabled());
        userViewModel.setAdmin(user.isAdmin());

        return userViewModel;
    }

}
