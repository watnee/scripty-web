package com.scripty.service;

import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;
import com.scripty.commandmodel.user.edituser.EditUserCommandModel;
import com.scripty.dto.Authority;
import com.scripty.dto.User;
import com.scripty.repository.AuthorityRepository;
import com.scripty.repository.UserRepository;
import com.scripty.security.PasswordPolicy;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.user.createuser.CreateUserViewModel;
import com.scripty.viewmodel.user.edituser.EditUserViewModel;
import com.scripty.viewmodel.user.userprofile.UserProfileViewModel;
import com.scripty.viewmodel.user.userlist.UserListViewModel;
import com.scripty.viewmodel.user.userlist.UserViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("appUserService")
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProjectService projectService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           AuthorityRepository authorityRepository,
                           PasswordEncoder passwordEncoder,
                           @Lazy ProjectService projectService) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
        this.projectService = projectService;
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
        if (user.isDirector()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_DIRECTOR"));
        }
        if (user.isProducer()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_PRODUCER"));
        }
        if (user.isWriter()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_WRITER"));
        }
        if (user.isActor()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_ACTOR"));
        }
        if (user.isCrew()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_CREW"));
        }
        if (user.isDirectorOfPhotography()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_DP"));
        }
        if (user.isCastingDirector()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_CASTING"));
        }
        if (user.isViewCasting()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_VIEW_CASTING"));
        }
        return saved;
    }

    @Override
    public User read(Integer id) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            applyAuthorities(user);
        }
        return user;
    }

    @Override
    public User readByUsername(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            applyAuthorities(user);
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
        existing.setEmail(user.getEmail());
        existing.setDefaultProjectId(user.getDefaultProjectId());
        // Only hash when the caller supplies a new plaintext password.
        // read()+update() paths (e.g. toggleDefault) pass the existing bcrypt
        // hash through; re-encoding it locks the user out.
        if (user.getPassword() != null && !user.getPassword().isEmpty()
                && !user.getPassword().equals(existing.getPassword())) {
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userRepository.save(existing);
        authorityRepository.deleteByUsername(user.getUsername());
        authorityRepository.save(new Authority(user.getUsername(), "ROLE_USER"));
        if (user.isAdmin()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_ADMIN"));
        }
        if (user.isDirector()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_DIRECTOR"));
        }
        if (user.isProducer()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_PRODUCER"));
        }
        if (user.isWriter()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_WRITER"));
        }
        if (user.isActor()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_ACTOR"));
        }
        if (user.isCrew()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_CREW"));
        }
        if (user.isDirectorOfPhotography()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_DP"));
        }
        if (user.isCastingDirector()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_CASTING"));
        }
        if (user.isViewCasting()) {
            authorityRepository.save(new Authority(user.getUsername(), "ROLE_VIEW_CASTING"));
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
            applyAuthorities(user);
        }
        return users;
    }

    @Override
    public UserListViewModel getUserListViewModel() {
        UserListViewModel vm = new UserListViewModel();
        List<User> users = list();
        var projectAccessByUserId = projectService.getUsersProjectAccess(users);
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
            uvm.setDirector(user.isDirector());
            uvm.setProducer(user.isProducer());
            uvm.setWriter(user.isWriter());
            uvm.setActor(user.isActor());
            uvm.setCrew(user.isCrew());
            uvm.setDirectorOfPhotography(user.isDirectorOfPhotography());
            uvm.setCastingDirector(user.isCastingDirector());
            uvm.setViewCasting(user.isViewCasting());
            uvm.setCanEditScreenplay(user.isWriter() || user.isAdmin());
            uvm.setCanViewCastingPages(user.isAdmin() || user.isCastingDirector() || user.isViewCasting());
            uvm.setPrivilegedProjectAccess(user.isAdmin() || user.isDirector() || user.isProducer()
                    || user.isWriter() || user.isActor() || user.isCrew()
                    || user.isDirectorOfPhotography() || user.isCastingDirector());
            uvm.setProjectAccess(projectAccessByUserId.getOrDefault(user.getId(), List.of()));
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
        commandModel.setDirector(user.isDirector());
        commandModel.setProducer(user.isProducer());
        commandModel.setWriter(user.isWriter());
        commandModel.setActor(user.isActor());
        commandModel.setCrew(user.isCrew());
        commandModel.setDirectorOfPhotography(user.isDirectorOfPhotography());
        commandModel.setCastingDirector(user.isCastingDirector());
        commandModel.setViewCasting(user.isViewCasting());
        vm.setEditUserCommandModel(commandModel);
        return vm;
    }

    @Override
    public User saveCreateUserCommandModel(CreateUserCommandModel cmd) {
        User user = new User();
        user.setUsername(PlainTextSanitizer.sanitizeSingleLine(cmd.getUsername()));
        user.setPassword(cmd.getPassword());
        user.setFirstName(PlainTextSanitizer.sanitizeSingleLine(cmd.getFirstName()));
        user.setLastName(PlainTextSanitizer.sanitizeSingleLine(cmd.getLastName()));
        user.setTeam(PlainTextSanitizer.sanitizeSingleLine(cmd.getTeam()));
        user.setEnabled(true);
        user.setAdmin(cmd.isAdmin());
        user.setDirector(cmd.isDirector());
        user.setProducer(cmd.isProducer());
        user.setWriter(cmd.isWriter());
        user.setActor(cmd.isActor());
        user.setCrew(cmd.isCrew());
        user.setDirectorOfPhotography(cmd.isDirectorOfPhotography());
        user.setCastingDirector(cmd.isCastingDirector());
        user.setViewCasting(cmd.isViewCasting());
        return create(user);
    }

    @Override
    public User saveEditUserCommandModel(EditUserCommandModel cmd) {
        User user = read(cmd.getId());
        user.setUsername(PlainTextSanitizer.sanitizeSingleLine(cmd.getUsername()));
        user.setPassword(cmd.getPassword());
        user.setFirstName(PlainTextSanitizer.sanitizeSingleLine(cmd.getFirstName()));
        user.setLastName(PlainTextSanitizer.sanitizeSingleLine(cmd.getLastName()));
        user.setTeam(PlainTextSanitizer.sanitizeSingleLine(cmd.getTeam()));
        user.setAdmin(cmd.isAdmin());
        user.setDirector(cmd.isDirector());
        user.setProducer(cmd.isProducer());
        user.setWriter(cmd.isWriter());
        user.setActor(cmd.isActor());
        user.setCrew(cmd.isCrew());
        user.setDirectorOfPhotography(cmd.isDirectorOfPhotography());
        user.setCastingDirector(cmd.isCastingDirector());
        user.setViewCasting(cmd.isViewCasting());
        update(user);
        return user;
    }

    @Override
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("User not found.");
        }
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password.");
        }
        if (PasswordPolicy.isWeak(newPassword, username)) {
            throw new IllegalArgumentException(
                    "New password is too weak: use at least " + PasswordPolicy.MIN_LENGTH
                            + " characters and avoid common passwords or your username.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public User deleteUser(Integer id, String actingUsername) {
        User user = read(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found.");
        }
        if (actingUsername != null && actingUsername.equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }
        delete(user);
        return user;
    }

    @Override
    public UserProfileViewModel getUserProfileViewModel(Integer id) {
        User user = read(id);
        UserProfileViewModel vm = new UserProfileViewModel();
        vm.setId(user.getId());
        vm.setUsername(user.getUsername());
        vm.setFirstName(user.getFirstName());
        vm.setLastName(user.getLastName());
        vm.setTeam(user.getTeam());
        vm.setEnabled(user.isEnabled());
        vm.setAdmin(user.isAdmin());
        vm.setDirector(user.isDirector());
        vm.setProducer(user.isProducer());
        vm.setWriter(user.isWriter());
        vm.setActor(user.isActor());
        vm.setCrew(user.isCrew());
        vm.setDirectorOfPhotography(user.isDirectorOfPhotography());
        vm.setCastingDirector(user.isCastingDirector());
        vm.setViewCasting(user.isViewCasting());
        vm.setCanEditScreenplay(user.isWriter() || user.isAdmin());
        vm.setCanViewCastingPages(user.isAdmin() || user.isCastingDirector() || user.isViewCasting());
        vm.setPrivilegedProjectAccess(user.isAdmin() || user.isDirector() || user.isProducer()
                || user.isWriter() || user.isActor() || user.isCrew()
                || user.isDirectorOfPhotography() || user.isCastingDirector());
        vm.setProjectAccess(projectService.getUserProjectAccess(user));
        return vm;
    }

    private void applyAuthorities(User user) {
        List<Authority> authorities = authorityRepository.findByUsername(user.getUsername());
        user.setAdmin(authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        user.setDirector(authorities.stream().anyMatch(a -> "ROLE_DIRECTOR".equals(a.getAuthority())));
        user.setProducer(authorities.stream().anyMatch(a -> "ROLE_PRODUCER".equals(a.getAuthority())));
        user.setWriter(authorities.stream().anyMatch(a -> "ROLE_WRITER".equals(a.getAuthority())));
        user.setActor(authorities.stream().anyMatch(a -> "ROLE_ACTOR".equals(a.getAuthority())));
        user.setCrew(authorities.stream().anyMatch(a -> "ROLE_CREW".equals(a.getAuthority())));
        user.setDirectorOfPhotography(authorities.stream().anyMatch(a -> "ROLE_DP".equals(a.getAuthority())));
        user.setCastingDirector(authorities.stream().anyMatch(a -> "ROLE_CASTING".equals(a.getAuthority())));
        user.setViewCasting(authorities.stream().anyMatch(a -> "ROLE_VIEW_CASTING".equals(a.getAuthority())));
    }
}
