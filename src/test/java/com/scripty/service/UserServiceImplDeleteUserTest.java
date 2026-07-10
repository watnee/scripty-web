package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserServiceImplDeleteUserTest {

    private UserRepository userRepository;
    private AuthorityRepository authorityRepository;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authorityRepository = mock(AuthorityRepository.class);
        userService = new UserServiceImpl(userRepository, authorityRepository, new BCryptPasswordEncoder(), mock(ProjectService.class));
    }

    @Test
    void deleteUserRejectsDeletingOwnAccount() {
        User self = new User();
        self.setId(3);
        self.setUsername("admin");
        when(userRepository.findById(3)).thenReturn(Optional.of(self));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUser(3, "admin"));

        assertEquals("You cannot delete your own account.", ex.getMessage());
        verify(userRepository, never()).delete(self);
        verify(authorityRepository, never()).deleteByUsername("admin");
    }

    @Test
    void deleteUserAllowsDeletingOtherAccount() {
        User other = new User();
        other.setId(4);
        other.setUsername("writer");
        when(userRepository.findById(4)).thenReturn(Optional.of(other));

        User deleted = userService.deleteUser(4, "admin");

        assertEquals("writer", deleted.getUsername());
        verify(authorityRepository).deleteByUsername("writer");
        verify(userRepository).delete(other);
    }
}
