package com.scripty.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.User;
import com.scripty.repository.UserRepository;
import com.scripty.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class DefaultAdminPasswordGuardTest {

    private UserRepository userRepository;
    private UserService userService;
    private PasswordEncoder passwordEncoder;
    private DefaultAdminPasswordGuard guard;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = mock(UserService.class);
        passwordEncoder = new BCryptPasswordEncoder();
        guard = new DefaultAdminPasswordGuard(userRepository, userService, passwordEncoder);
        ReflectionTestUtils.setField(guard, "adminUsername", "admin");
        ReflectionTestUtils.setField(guard, "adminPassword", "");
        ReflectionTestUtils.setField(guard, "adminPasswordReset", false);
    }

    @Test
    void rotatesAdminStillOnDefaultPasswordToGeneratedOne() {
        User admin = user("admin", passwordEncoder.encode("admin"));
        when(userRepository.count()).thenReturn(1L);
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(admin));

        guard.run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertFalse(passwordEncoder.matches("admin", saved.getValue().getPassword()));
        assertTrue(saved.getValue().isPasswordChangeRequired());
    }

    @Test
    void rotatesAdminToStrongEnvPasswordWithoutForcedChange() {
        ReflectionTestUtils.setField(guard, "adminPassword", "a-much-longer-secret");
        User admin = user("admin", passwordEncoder.encode("admin"));
        when(userRepository.count()).thenReturn(1L);
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(admin));

        guard.run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertTrue(passwordEncoder.matches("a-much-longer-secret", saved.getValue().getPassword()));
        assertFalse(saved.getValue().isPasswordChangeRequired());
    }

    @Test
    void flagsNonAdminUserOnWellKnownPasswordWithoutRotating() {
        String weakHash = passwordEncoder.encode("clint");
        User clint = user("clint", weakHash);
        when(userRepository.count()).thenReturn(1L);
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(clint));

        guard.run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertTrue(saved.getValue().isPasswordChangeRequired());
        assertTrue(passwordEncoder.matches("clint", saved.getValue().getPassword()));
    }

    @Test
    void leavesStrongPasswordsUntouched() {
        User admin = user("admin", passwordEncoder.encode("correct-horse-battery"));
        when(userRepository.count()).thenReturn(1L);
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(admin));

        guard.run();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void skipsDisabledUsers() {
        User disabled = user("old", passwordEncoder.encode("old"));
        disabled.setEnabled(false);
        when(userRepository.count()).thenReturn(1L);
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(disabled));

        guard.run();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createsAdminWithGeneratedPasswordOnEmptyDatabase() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        guard.run();

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(userService).create(created.capture());
        assertTrue(created.getValue().isAdmin());
        assertTrue(created.getValue().isEnabled());
        assertFalse("admin".equals(created.getValue().getPassword()));
    }

    @Test
    void breakGlassResetUsesEnvPassword() {
        ReflectionTestUtils.setField(guard, "adminPasswordReset", true);
        ReflectionTestUtils.setField(guard, "adminPassword", "a-much-longer-secret");
        User admin = user("admin", passwordEncoder.encode("forgotten-old-password"));
        when(userRepository.count()).thenReturn(1L);
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(admin));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        guard.run();

        assertTrue(passwordEncoder.matches("a-much-longer-secret", admin.getPassword()));
        assertFalse(admin.isPasswordChangeRequired());
    }

    @Test
    void breakGlassResetRefusesWeakEnvPassword() {
        ReflectionTestUtils.setField(guard, "adminPasswordReset", true);
        ReflectionTestUtils.setField(guard, "adminPassword", "admin");
        User admin = user("admin", passwordEncoder.encode("forgotten-old-password"));
        when(userRepository.count()).thenReturn(1L);
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(admin));

        guard.run();

        assertTrue(passwordEncoder.matches("forgotten-old-password", admin.getPassword()));
    }

    private static User user(String username, String passwordHash) {
        User user = new User();
        user.setId(1);
        user.setUsername(username);
        user.setPassword(passwordHash);
        user.setEnabled(true);
        return user;
    }
}
