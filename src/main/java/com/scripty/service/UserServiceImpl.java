package com.scripty.service;

import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;
import com.scripty.commandmodel.user.edituser.EditUserCommandModel;
import com.scripty.dto.Authority;
import com.scripty.dto.User;
import com.scripty.repository.AuthorityRepository;
import com.scripty.repository.UserRepository;
import com.scripty.viewmodel.user.createuser.CreateUserViewModel;
import com.scripty.viewmodel.user.edituser.EditUserViewModel;
import com.scripty.viewmodel.user.accountprofile.AccountProfileViewModel;
import com.scripty.viewmodel.user.userlist.UserListViewModel;
import com.scripty.viewmodel.user.userlist.UserViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("appUserService")
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           AuthorityRepository authorityRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User create(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User saved = userRepository.save(user);
        authorityRepository.save(new Authority(user.getUsername(), "ROLE_USER"));
        if (user.isAdmin()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_ADMIN"));
        }
        return saved;
    }

    @Override
    public User read(Integer id) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            List<Authority> authorities = authorityRepository.findByUsername(user.getUsername());
            user.setAdmin(authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        }
        return user;
    }

    @Override
    public User readByUsername(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            List<Authority> authorities = authorityRepository.findByUsername(user.getUsername());
            user.setAdmin(authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        }
        return user;
    }

    @Override
    @Transactional
    public void update(User user) {
        User existing = userRepository.findById(user.getId()).orElse(null);
        if (existing == null) return;
        existing.setUsername(user.getUsername());
        existing.setEnabled(user.isEnabled());
        existing.setFirstName(user.getFirstName());
        existing.setLastName(user.getLastName());
        existing.setTeam(user.getTeam());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userRepository.save(existing);
        authorityRepository.deleteByUsername(user.getUsername());
        authorityRepository.save(new Authority(user.getUsername(), "ROLE_USER"));
        if (user.isAdmin()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_ADMIN"));
        }
    }

    @Override
    @Transactional
    public void delete(User user) {
        authorityRepository.deleteByUsername(user.getUsername());
        userRepository.delete(user);
    }

    @Override
    public List<User> list() {
        List<User> users = userRepository.findAllByOrderByUsernameAsc();
        for (User user : users) {
            List<Authority> authorities = authorityRepository.findByUsername(user.getUsername());
            user.setAdmin(authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        }
        return users;
    }

    @Override
    public UserListViewModel getUserListViewModel() {
        UserListViewModel vm = new UserListViewModel();
        List<User> users = list();
        List<UserViewModel> userViewModels = new ArrayList<>();
        for (User user : users) {
            UserViewModel uvm = new UserViewModel();
            uvm.setId(user.getId());
            uvm.setUsername(user.getUsername());
            uvm.setFirstName(user.getFirstName());
            uvm.setLastName(user.getLastName());
            uvm.setTeam(user.getTeam());
            uvm.setEnabled(user.isEnabled());
            uvm.setAdmin(user.isAdmin());
            userViewModels.add(uvm);
        }
        vm.setUsers(userViewModels);
        return vm;
    }

    @Override
    public CreateUserViewModel getCreateUserViewModel() {
        CreateUserViewModel vm = new CreateUserViewModel();
        vm.setCreateUserCommandModel(new CreateUserCommandModel());
        return vm;
    }

    @Override
    public EditUserViewModel getEditUserViewModel(Integer id) {
        EditUserViewModel vm = new EditUserViewModel();
        User user = read(id);
        vm.setId(id);
        EditUserCommandModel commandModel = new EditUserCommandModel();
        commandModel.setId(user.getId());
        commandModel.setUsername(user.getUsername());
        commandModel.setFirstName(user.getFirstName());
        commandModel.setLastName(user.getLastName());
        commandModel.setTeam(user.getTeam());
        commandModel.setAdmin(user.isAdmin());
        vm.setEditUserCommandModel(commandModel);
        return vm;
    }

    @Override
    public User saveCreateUserCommandModel(CreateUserCommandModel cmd) {
        User user = new User();
        user.setUsername(cmd.getUsername());
        user.setPassword(cmd.getPassword());
        user.setFirstName(cmd.getFirstName());
        user.setLastName(cmd.getLastName());
        user.setTeam(cmd.getTeam());
        user.setEnabled(true);
        user.setAdmin(cmd.isAdmin());
        return create(user);
    }

    @Override
    public User saveEditUserCommandModel(EditUserCommandModel cmd) {
        User user = read(cmd.getId());
        user.setUsername(cmd.getUsername());
        user.setPassword(cmd.getPassword());
        user.setFirstName(cmd.getFirstName());
        user.setLastName(cmd.getLastName());
        user.setTeam(cmd.getTeam());
        user.setAdmin(cmd.isAdmin());
        update(user);
        return user;
    }

    @Override
    @Transactional
    public User deleteUser(Integer id) {
        User user = read(id);
        delete(user);
        return user;
    }

    @Override
    public AccountProfileViewModel getAccountProfileViewModel(Integer id) {
        User user = read(id);
        AccountProfileViewModel vm = new AccountProfileViewModel();
        vm.setId(user.getId());
        vm.setUsername(user.getUsername());
        vm.setFirstName(user.getFirstName());
        vm.setLastName(user.getLastName());
        vm.setTeam(user.getTeam());
        vm.setEnabled(user.isEnabled());
        vm.setAdmin(user.isAdmin());
        return vm;
    }
}
