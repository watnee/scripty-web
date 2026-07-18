package com.scripty.service;

import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;
import com.scripty.commandmodel.user.edituser.EditUserCommandModel;
import com.scripty.dto.User;
import com.scripty.viewmodel.user.createuser.CreateUserViewModel;
import com.scripty.viewmodel.user.edituser.EditUserViewModel;
import com.scripty.viewmodel.user.userprofile.UserProfileViewModel;
import com.scripty.viewmodel.user.userlist.UserListViewModel;
import java.util.List;

public interface UserService {

    User create(User user);
    User read(Integer id);
    User readByUsername(String username);
    void update(User user);
    void delete(User user);
    List<User> list();

    UserListViewModel getUserListViewModel();

    CreateUserViewModel getCreateUserViewModel();
    EditUserViewModel getEditUserViewModel(Integer id);

    User saveCreateUserCommandModel(CreateUserCommandModel createUserCommandModel);
    User saveEditUserCommandModel(EditUserCommandModel editUserCommandModel);

    void changePassword(String username, String currentPassword, String newPassword);

    User deleteUser(Integer id, String actingUsername);

    UserProfileViewModel getUserProfileViewModel(Integer id);

    /** Falls back to {@link CapitalizationPreferences#ALL} for unknown/anonymous users. */
    CapitalizationPreferences readCapitalizationPreferences(String username);

    CapitalizationPreferences updateCapitalizationPreferences(String username, CapitalizationPreferences preferences);
}
