package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.scripty.dto.Authority;
import com.scripty.dto.User;
import com.scripty.repository.AuthorityRepository;
import com.scripty.repository.UserRepository;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceImplDeveloperRoleTest {

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
    void createDeveloperOnlyUserAssignsOnlyRoleDeveloper() {
        User user = new User();
        user.setUsername("devuser");
        user.setPassword("password");
        user.setDeveloper(true);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(user);

        ArgumentCaptor<Authority> authorityCaptor = ArgumentCaptor.forClass(Authority.class);
        verify(authorityRepository, times(1)).save(authorityCaptor.capture());

        List<Authority> savedAuthorities = authorityCaptor.getAllValues();
        assertEquals(1, savedAuthorities.size());
        assertEquals("ROLE_DEVELOPER", savedAuthorities.get(0).getAuthority());
    }

    @Test
    void createStandardUserAssignsRoleUser() {
        User user = new User();
        user.setUsername("stduser");
        user.setPassword("password");
        user.setDeveloper(false);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(user);

        ArgumentCaptor<Authority> authorityCaptor = ArgumentCaptor.forClass(Authority.class);
        verify(authorityRepository, times(1)).save(authorityCaptor.capture());

        List<Authority> savedAuthorities = authorityCaptor.getAllValues();
        assertEquals(1, savedAuthorities.size());
        assertEquals("ROLE_USER", savedAuthorities.get(0).getAuthority());
    }

    @Test
    void createDeveloperAndAdminAssignsRoleUserRoleAdminAndRoleDeveloper() {
        User user = new User();
        user.setUsername("admindev");
        user.setPassword("password");
        user.setDeveloper(true);
        user.setAdmin(true);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(user);

        ArgumentCaptor<Authority> authorityCaptor = ArgumentCaptor.forClass(Authority.class);
        verify(authorityRepository, times(3)).save(authorityCaptor.capture());

        List<Authority> savedAuthorities = authorityCaptor.getAllValues();
        assertEquals(3, savedAuthorities.size());
        assertTrue(savedAuthorities.stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
        assertTrue(savedAuthorities.stream().anyMatch(a -> "ROLE_DEVELOPER".equals(a.getAuthority())));
        assertTrue(savedAuthorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }
}
