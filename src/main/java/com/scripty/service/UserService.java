package com.scripty.service;

import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;
import com.scripty.commandmodel.user.edituser.EditUserCommandModel;
import com.scripty.dto.User;
import com.scripty.viewmodel.user.createuser.CreateUserViewModel;
import com.scripty.viewmodel.user.edituser.EditUserViewModel;
import com.scripty.viewmodel.user.userlist.UserListViewModel;
import java.util.List;

public interface UserService {

    User create(User user);
    User read(Integer id);
    void update(User user);
    void delete(User user);
    List<User> list();

    UserListViewModel getUserListViewModel();

    CreateUserViewModel getCreateUserViewModel();
    EditUserViewModel getEditUserViewModel(Integer id);

    User saveCreateUserCommandModel(CreateUserCommandModel createUserCommandModel);
    User saveEditUserCommandModel(EditUserCommandModel editUserCommandModel);

    User deleteUser(Integer id);
}
