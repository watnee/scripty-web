package com.scripty.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.scripty.dto.User;
import com.scripty.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailResolvingUserDetailsManagerTest {

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
    }

    @Test
    void plainUsernamePassesThroughWithoutEmailLookup() {
        String resolved = EmailResolvingUserDetailsManager
                .resolveToUsername(userRepository, "admin");

        assertEquals("admin", resolved);
        verifyNoInteractions(userRepository);
    }

    @Test
    void emailResolvesToAccountUsername() {
        User user = new User();
        user.setUsername("testuser");
        when(userRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(user));

        String resolved = EmailResolvingUserDetailsManager
                .resolveToUsername(userRepository, "user@example.com");

        assertEquals("testuser", resolved);
    }

    @Test
    void emailIsTrimmedBeforeLookup() {
        User user = new User();
        user.setUsername("testuser");
        when(userRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(user));

        String resolved = EmailResolvingUserDetailsManager
                .resolveToUsername(userRepository, "  user@example.com ");

        assertEquals("testuser", resolved);
    }

    @Test
    void unknownEmailFallsThroughUnchanged() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com"))
                .thenReturn(Optional.empty());

        String resolved = EmailResolvingUserDetailsManager
                .resolveToUsername(userRepository, "nobody@example.com");

        assertEquals("nobody@example.com", resolved);
    }

    @Test
    void nullLoginPassesThrough() {
        assertNull(EmailResolvingUserDetailsManager.resolveToUsername(userRepository, null));
        verifyNoInteractions(userRepository);
    }
}
