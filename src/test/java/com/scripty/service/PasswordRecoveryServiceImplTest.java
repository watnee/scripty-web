package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.PasswordRecoveryToken;
import com.scripty.dto.User;
import com.scripty.repository.PasswordRecoveryTokenRepository;
import com.scripty.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordRecoveryServiceImplTest {

    private UserRepository userRepository;
    private PasswordRecoveryTokenRepository tokenRepository;
    private EmailService emailService;
    private PasswordEncoder passwordEncoder;
    private PasswordRecoveryServiceImpl recoveryService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordRecoveryTokenRepository.class);
        emailService = mock(EmailService.class);
        passwordEncoder = new BCryptPasswordEncoder();
        recoveryService = new PasswordRecoveryServiceImpl(
                userRepository, tokenRepository, emailService, passwordEncoder);
    }

    @Test
    void sendRecoveryEmailGeneratesTokenAndSendsEmailWhenUserExists() {
        User user = new User();
        user.setId(42);
        user.setUsername("testuser");
        user.setEmail("user@example.com");
        user.setFirstName("Test");

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        recoveryService.sendRecoveryEmail("user@example.com");

        verify(tokenRepository).deleteByUser(user);
        ArgumentCaptor<PasswordRecoveryToken> tokenCaptor = ArgumentCaptor.forClass(PasswordRecoveryToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());

        PasswordRecoveryToken savedToken = tokenCaptor.getValue();
        assertEquals(user, savedToken.getUser());
        assertNotNull(savedToken.getToken());
        assertTrue(savedToken.getExpiresAt().isAfter(LocalDateTime.now()));

        verify(emailService).send(
                eq("user@example.com"),
                eq("Reset your Scripty password"),
                any(String.class)
        );
    }

    @Test
    void sendRecoveryEmailFailsSilentlyWhenUserDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        recoveryService.sendRecoveryEmail("missing@example.com");

        verify(tokenRepository, never()).deleteByUser(any(User.class));
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
        verify(emailService, never()).send(any(), any(), any());
    }

    @Test
    void validateTokenReturnsTokenWhenValid() {
        PasswordRecoveryToken token = new PasswordRecoveryToken();
        token.setToken("valid-token");
        token.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        PasswordRecoveryToken result = recoveryService.validateToken("valid-token");
        assertEquals(token, result);
    }

    @Test
    void validateTokenThrowsWhenExpired() {
        PasswordRecoveryToken token = new PasswordRecoveryToken();
        token.setToken("expired-token");
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> recoveryService.validateToken("expired-token"));
        assertEquals("The password reset token has expired.", ex.getMessage());
    }

    @Test
    void validateTokenThrowsWhenNotFound() {
        when(tokenRepository.findByToken("nonexistent")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> recoveryService.validateToken("nonexistent"));
        assertEquals("Invalid password reset token.", ex.getMessage());
    }

    @Test
    void resetPasswordUpdatesPasswordAndDeletesTokenWhenValid() {
        User user = new User();
        user.setId(42);
        user.setUsername("testuser");
        user.setPassword("old-hashed-password");
        user.setPasswordChangeRequired(true);

        PasswordRecoveryToken token = new PasswordRecoveryToken();
        token.setToken("valid-token");
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        token.setUser(user);

        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        recoveryService.resetPassword("valid-token", "strong-new-password");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User updatedUser = userCaptor.getValue();
        assertTrue(passwordEncoder.matches("strong-new-password", updatedUser.getPassword()));
        assertFalse(updatedUser.isPasswordChangeRequired());

        verify(tokenRepository).deleteByUser(user);
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        User user = new User();
        user.setId(42);
        user.setUsername("testuser");

        PasswordRecoveryToken token = new PasswordRecoveryToken();
        token.setToken("valid-token");
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        token.setUser(user);

        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> recoveryService.resetPassword("valid-token", "changeme"));
        assertTrue(ex.getMessage().contains("too weak"));
        verify(userRepository, never()).save(any(User.class));
    }
}
