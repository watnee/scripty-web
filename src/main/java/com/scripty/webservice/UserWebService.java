package com.scripty.webservice;

import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;
import com.scripty.commandmodel.user.edituser.EditUserCommandModel;
import com.scripty.dto.User;
import com.scripty.viewmodel.user.createuser.CreateUserViewModel;
import com.scripty.viewmodel.user.edituser.EditUserViewModel;
import com.scripty.viewmodel.user.userlist.UserListViewModel;

public interface UserWebService {

    public UserListViewModel getUserListViewModel();

    public CreateUserViewModel getCreateUserViewModel();
    public EditUserViewModel getEditUserViewModel(Integer id);

    public User saveCreateUserCommandModel(CreateUserCommandModel createUserCommandModel);
    public User saveEditUserCommandModel(EditUserCommandModel editUserCommandModel);

    public User deleteUser(Integer id);

}
