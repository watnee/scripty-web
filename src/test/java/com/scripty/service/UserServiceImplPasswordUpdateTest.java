package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

class UserServiceImplPasswordUpdateTest {

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
    void updateDoesNotRehashExistingPasswordFromReadUpdate() {
        String storedHash = passwordEncoder.encode("admin");
        User existing = baseUser(storedHash);

        when(userRepository.findById(1)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Mimic ProjectController.toggleDefault: read user (hash loaded) then update.
        User fromRead = baseUser(storedHash);
        fromRead.setDefaultProjectId(14);

        userService.update(fromRead);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(storedHash, saved.getValue().getPassword());
    }

    @Test
    void updateHashesNewPlaintextPassword() {
        String storedHash = passwordEncoder.encode("old-password");
        User existing = baseUser(storedHash);

        when(userRepository.findById(1)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User edited = baseUser("new-password");

        userService.update(edited);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        String newHash = saved.getValue().getPassword();
        assertNotEquals(storedHash, newHash);
        assertNotEquals("new-password", newHash);
        assertTrue(passwordEncoder.matches("new-password", newHash));
    }

    @Test
    void updateSkipsBlankPassword() {
        String storedHash = passwordEncoder.encode("admin");
        User existing = baseUser(storedHash);

        when(userRepository.findById(1)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User edited = baseUser(null);

        userService.update(edited);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(storedHash, saved.getValue().getPassword());
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
