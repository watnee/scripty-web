package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.PasswordRecoveryToken;
import com.scripty.dto.User;
import com.scripty.repository.PasswordRecoveryTokenRepository;
import com.scripty.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class PasswordRecoveryServiceImplTest {

    private UserRepository userRepository;
    private PasswordRecoveryTokenRepository tokenRepository;
    private EmailService emailService;
    private PasswordEncoder passwordEncoder;
    private ApiTokenService apiTokenService;
    private PersistentTokenRepository persistentTokenRepository;
    private PasswordRecoveryServiceImpl recoveryService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordRecoveryTokenRepository.class);
        emailService = mock(EmailService.class);
        passwordEncoder = new BCryptPasswordEncoder();
        apiTokenService = mock(ApiTokenService.class);
        persistentTokenRepository = mock(PersistentTokenRepository.class);
        recoveryService = new PasswordRecoveryServiceImpl(
                userRepository, tokenRepository, emailService, passwordEncoder,
                apiTokenService, persistentTokenRepository, templateEngine());
        ReflectionTestUtils.setField(recoveryService, "baseUrl", "https://scripty.example");
        ReflectionTestUtils.setField(recoveryService, "supportEmail", "support@scripty.example");
    }

    /**
     * The real templates, not a stubbed engine. What goes in these emails is
     * half of what this class does — a mock returning "body" would let the
     * link, the escaping and the plain-text half all rot unnoticed.
     *
     * <p>Spring's engine rather than the plain one, because that is the bean
     * Boot supplies: the two evaluate expressions with different languages
     * (SpEL against OGNL), so a template that renders under one is no evidence
     * about the other.
     */
    private static SpringTemplateEngine templateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        // Mirrors the resolver Boot configures: bare names, .html appended.
        ClassLoaderTemplateResolver html = new ClassLoaderTemplateResolver();
        html.setPrefix("templates/");
        html.setSuffix(".html");
        html.setTemplateMode(TemplateMode.HTML);
        html.setCharacterEncoding("UTF-8");
        html.setOrder(1);
        engine.addTemplateResolver(html);

        // Mirrors com.scripty.config.MailTemplateConfig.
        ClassLoaderTemplateResolver text = new ClassLoaderTemplateResolver();
        text.setPrefix("templates/");
        text.setSuffix("");
        text.setResolvablePatterns(Set.of("email/*.txt"));
        text.setTemplateMode(TemplateMode.TEXT);
        text.setCharacterEncoding("UTF-8");
        text.setOrder(0);
        engine.addTemplateResolver(text);
        return engine;
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static User user() {
        User user = new User();
        user.setId(42);
        user.setUsername("testuser");
        user.setEmail("user@example.com");
        user.setFirstName("Test");
        return user;
    }

    /** The HTML body of the single email the mock was asked to send. */
    private String capturedHtml() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(any(), any(), html.capture(), any(), isNull());
        return html.getValue();
    }

    /** The plain-text body of the single email the mock was asked to send. */
    private String capturedText() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(any(), any(), any(), text.capture(), isNull());
        return text.getValue();
    }

    private PasswordRecoveryToken capturedToken() {
        ArgumentCaptor<PasswordRecoveryToken> captor =
                ArgumentCaptor.forClass(PasswordRecoveryToken.class);
        verify(tokenRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendRecoveryEmailGeneratesTokenAndSendsEmailWhenUserExists() {
        User user = user();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        recoveryService.sendRecoveryEmail("user@example.com");

        verify(tokenRepository).deleteByUser(user);
        PasswordRecoveryToken savedToken = capturedToken();
        assertEquals(user, savedToken.getUser());
        assertNotNull(savedToken.getTokenHash());
        assertTrue(savedToken.getExpiresAt().isAfter(LocalDateTime.now()));

        verify(emailService).send(
                eq("user@example.com"),
                eq("Reset your Scripty password"),
                any(String.class),
                any(String.class),
                isNull());
    }

    /**
     * The email carries the token, and carries it in the link rather than as
     * something to read off and retype. That is what lets a tap in Mail open
     * the app at the password field: both the app and the web page take the
     * token out of the URL, so there is never a code to copy.
     */
    @Test
    void theEmailCarriesTheTokenInTheLink() {
        User user = user();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        recoveryService.sendRecoveryEmail("user@example.com");

        String html = capturedHtml();
        String link = html.substring(html.indexOf("/forgot-password/reset?token="));
        String rawToken = link.substring(link.indexOf('=') + 1, link.indexOf('"'));

        // The row can recognise that token and could not have produced it.
        assertEquals(sha256Hex(rawToken), capturedToken().getTokenHash());
        assertTrue(capturedText().contains("/forgot-password/reset?token=" + rawToken),
                "the plain-text half must carry the same link");
    }

    /**
     * What is stored is a digest, so a database read cannot reset the account.
     * The raw token exists in the email and nowhere else.
     */
    @Test
    void theStoredTokenIsNotTheOneInTheEmail() {
        User user = user();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        recoveryService.sendRecoveryEmail("user@example.com");

        String storedHash = capturedToken().getTokenHash();
        assertEquals(64, storedHash.length(), "a SHA-256 hex digest is 64 characters");
        assertFalse(capturedHtml().contains(storedHash),
                "the digest must never appear in the email — only the value it was made from");
    }

    /** Two requests must not produce the same token, however close together. */
    @Test
    void everyRequestMintsADifferentToken() {
        User user = user();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        recoveryService.sendRecoveryEmail("user@example.com");
        recoveryService.sendRecoveryEmail("user@example.com");

        ArgumentCaptor<PasswordRecoveryToken> captor =
                ArgumentCaptor.forClass(PasswordRecoveryToken.class);
        verify(tokenRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(2, Set.copyOf(captor.getAllValues().stream()
                .map(PasswordRecoveryToken::getTokenHash).toList()).size());
    }

    /**
     * A name is display text from a user's own profile, and it lands in a
     * document that is rendered as markup. Escaping is the template's job; this
     * is the test that notices if someone swaps in string concatenation.
     */
    @Test
    void theNameInTheEmailIsEscaped() {
        User user = user();
        user.setFirstName("<script>alert(1)</script>");
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        recoveryService.sendRecoveryEmail("user@example.com");

        String html = capturedHtml();
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    /** No first name is a plain "Hello," — never "Hello null,". */
    @Test
    void aMissingFirstNameDoesNotLeakIntoTheGreeting() {
        User user = user();
        user.setFirstName(null);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        recoveryService.sendRecoveryEmail("user@example.com");

        assertFalse(capturedHtml().contains("null"));
        assertFalse(capturedText().contains("null"));
        assertTrue(capturedText().contains("Hello,"));
    }

    @Test
    void sendRecoveryEmailFailsSilentlyWhenUserDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        recoveryService.sendRecoveryEmail("missing@example.com");

        verify(tokenRepository, never()).deleteByUser(any(User.class));
        verify(tokenRepository, never()).save(any(PasswordRecoveryToken.class));
        verify(emailService, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void validateTokenReturnsTokenWhenValid() {
        PasswordRecoveryToken token = new PasswordRecoveryToken();
        token.setTokenHash(sha256Hex("valid-token"));
        token.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(tokenRepository.findByTokenHash(sha256Hex("valid-token"))).thenReturn(Optional.of(token));

        PasswordRecoveryToken result = recoveryService.validateToken("valid-token");
        assertEquals(token, result);
    }

    @Test
    void validateTokenThrowsWhenExpired() {
        PasswordRecoveryToken token = new PasswordRecoveryToken();
        token.setTokenHash(sha256Hex("expired-token"));
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(tokenRepository.findByTokenHash(sha256Hex("expired-token"))).thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> recoveryService.validateToken("expired-token"));
        assertEquals("The password reset token has expired.", ex.getMessage());
    }

    @Test
    void validateTokenThrowsWhenNotFound() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> recoveryService.validateToken("nonexistent"));
        assertEquals("Invalid password reset token.", ex.getMessage());
    }

    private PasswordRecoveryToken liveTokenFor(User user, String raw) {
        PasswordRecoveryToken token = new PasswordRecoveryToken();
        token.setTokenHash(sha256Hex(raw));
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        token.setUser(user);
        when(tokenRepository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void resetPasswordUpdatesPasswordAndDeletesTokenWhenValid() {
        User user = user();
        user.setPassword("old-hashed-password");
        user.setPasswordChangeRequired(true);
        liveTokenFor(user, "valid-token");

        recoveryService.resetPassword("valid-token", "strong-new-password");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User updatedUser = userCaptor.getValue();
        assertTrue(passwordEncoder.matches("strong-new-password", updatedUser.getPassword()));
        assertFalse(updatedUser.isPasswordChangeRequired());

        verify(tokenRepository).deleteByUser(user);
    }

    /**
     * A reset happens because the old password is not trusted any more. Anything
     * minted off it has to go too, or whoever got in keeps a way back that
     * changing the password did nothing about.
     */
    @Test
    void resetPasswordRevokesEverythingTheOldPasswordOpened() {
        User user = user();
        liveTokenFor(user, "valid-token");

        recoveryService.resetPassword("valid-token", "strong-new-password");

        verify(apiTokenService).revokeAll("testuser");
        verify(persistentTokenRepository).removeUserTokens("testuser");
    }

    /** Losing a remember-me sweep must not lose the password the user just set. */
    @Test
    void resetPasswordStandsEvenWhenRevocationFails() {
        User user = user();
        liveTokenFor(user, "valid-token");
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(apiTokenService).revokeAll("testuser");

        recoveryService.resetPassword("valid-token", "strong-new-password");

        verify(userRepository).save(any(User.class));
        verify(persistentTokenRepository).removeUserTokens("testuser");
    }

    /**
     * The one message nobody asked for, and the reason it is worth sending: if
     * the reset was not theirs, this is how the account's owner finds out. It
     * carries no link and no token — a warning that someone may have taken the
     * account must not itself be another way in.
     */
    @Test
    void resetPasswordTellsTheOwnerTheirPasswordChanged() {
        User user = user();
        liveTokenFor(user, "valid-token");

        recoveryService.resetPassword("valid-token", "strong-new-password");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("user@example.com"), eq("Your Scripty password was changed"),
                html.capture(), any(String.class), isNull());
        assertTrue(html.getValue().contains("support@scripty.example"));
        assertFalse(html.getValue().contains("/forgot-password/reset?token="),
                "a breach notice must not carry a credential");
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        User user = user();
        liveTokenFor(user, "valid-token");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> recoveryService.resetPassword("valid-token", "changeme"));
        assertTrue(ex.getMessage().contains("too weak"));
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).send(any(), any(), any(), any(), any());
        verify(apiTokenService, never()).revokeAll(any());
    }
}
