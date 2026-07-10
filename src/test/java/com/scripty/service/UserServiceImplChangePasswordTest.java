package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.User;
import com.scripty.repository.AuthorityRepository;
import com.scripty.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceImplChangePasswordTest {

    private UserRepository userRepository;
    private AuthorityRepository authorityRepository;
    private PasswordEncoder passwordEncoder;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authorityRepository = mock(AuthorityRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserServiceImpl(userRepository, authorityRepository, passwordEncoder, mock(ProjectService.class));
    }

    @Test
    void changePasswordUpdatesHashWhenCurrentPasswordMatches() {
        String storedHash = passwordEncoder.encode("old-password");
        User existing = baseUser(storedHash);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.changePassword("admin", "old-password", "new-password");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertTrue(passwordEncoder.matches("new-password", saved.getValue().getPassword()));
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        String storedHash = passwordEncoder.encode("old-password");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(baseUser(storedHash)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword("admin", "wrong-password", "new-password"));

        assertEquals("Current password is incorrect.", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordRejectsSameAsCurrentPassword() {
        String storedHash = passwordEncoder.encode("same-password");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(baseUser(storedHash)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword("admin", "same-password", "same-password"));

        assertEquals("New password must be different from the current password.", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordRejectsUnknownUser() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword("missing", "old-password", "new-password"));

        assertEquals("User not found.", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    private static User baseUser(String password) {
        User user = new User();
        user.setId(1);
        user.setUsername("admin");
        user.setPassword(password);
        user.setEnabled(true);
        return user;
    }
}
