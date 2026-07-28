package com.scripty.service;

import com.scripty.dto.PasswordRecoveryToken;
import com.scripty.dto.User;
import com.scripty.repository.PasswordRecoveryTokenRepository;
import com.scripty.repository.UserRepository;
import com.scripty.security.PasswordPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryServiceImpl.class);

    /** How long a link in an inbox stays good for. */
    private static final int EXPIRY_HOURS = 2;

    /** 256 bits, the same strength {@link ApiTokenService} mints at. */
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordRecoveryTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final ApiTokenService apiTokenService;
    private final PersistentTokenRepository persistentTokenRepository;
    private final ITemplateEngine templateEngine;

    /**
     * Raw tokens are never stored, so they have to be unguessable rather than
     * merely unique — {@link java.util.UUID#randomUUID()} would do for the
     * second and is the wrong tool for the first.
     */
    private final SecureRandom random = new SecureRandom();

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.support-email:support@scripty.app}")
    private String supportEmail;

    @Autowired
    public PasswordRecoveryServiceImpl(UserRepository userRepository,
                                       PasswordRecoveryTokenRepository tokenRepository,
                                       EmailService emailService,
                                       PasswordEncoder passwordEncoder,
                                       ApiTokenService apiTokenService,
                                       PersistentTokenRepository persistentTokenRepository,
                                       ITemplateEngine templateEngine) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.apiTokenService = apiTokenService;
        this.persistentTokenRepository = persistentTokenRepository;
        this.templateEngine = templateEngine;
    }

    @Override
    @Transactional
    public void sendRecoveryEmail(String email) {
        if (email == null || email.isBlank()) {
            log.warn("Attempt to recover password with empty email.");
            return;
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            log.info("Password recovery requested for non-existent email: {}", email);
            return; // Fail silently to prevent email enumeration
        }

        // Clean up any existing tokens for this user first
        tokenRepository.deleteByUser(user);

        // The raw token goes in the email and nowhere else; the row keeps only
        // its digest, so a database read cannot reset anyone's account.
        String rawToken = newRawToken();
        PasswordRecoveryToken token = new PasswordRecoveryToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS));

        tokenRepository.save(token);

        // One link, and nothing to copy out of it: on a device with the app
        // installed this opens Scripty straight at "choose a new password" (the
        // app claims this path in the site association file), and anywhere else
        // it opens the same page in a browser. Both ends read the token out of
        // the URL, so there is never a code to retype.
        String resetUrl = baseUrl + "/forgot-password/reset?token=" + rawToken;

        Context context = new Context();
        context.setVariable("firstName", displayName(user));
        context.setVariable("email", user.getEmail());
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("expiryHours", EXPIRY_HOURS);

        // Never the token itself: anyone who can read the logs could reset the
        // account with it, which is the same rule the invitation mail follows.
        log.info("Sending password recovery email to={}", user.getEmail());
        sendAfterCommit(user.getEmail(), "Reset your Scripty password", "email/password-reset", context);
    }

    @Override
    @Transactional(readOnly = true)
    public PasswordRecoveryToken validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token must not be empty.");
        }

        PasswordRecoveryToken recoveryToken = tokenRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token."));

        if (recoveryToken.isExpired()) {
            throw new IllegalArgumentException("The password reset token has expired.");
        }

        // The user is fetched lazily; initialize it here so callers outside this
        // transaction (the controller renders the username) don't hit a closed session.
        Hibernate.initialize(recoveryToken.getUser());

        return recoveryToken;
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        // Validate token first
        PasswordRecoveryToken recoveryToken = validateToken(token);
        User user = recoveryToken.getUser();

        // Validate password strength
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }

        if (PasswordPolicy.isWeak(newPassword, user.getUsername())) {
            throw new IllegalArgumentException(
                    "Password is too weak: use at least " + PasswordPolicy.MIN_LENGTH
                            + " characters and avoid common passwords or your username.");
        }

        // Hash and save new password
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        userRepository.save(user);

        // Delete/consume the token
        tokenRepository.deleteByUser(user);

        // A reset exists because the old password is not trusted any more —
        // either it was forgotten or somebody else has it. Anything minted off
        // that password has to go with it, or an attacker who got in keeps a
        // way back that changing the password did nothing about. Remember-me
        // cookies and API bearer tokens are both exactly that.
        revokeStandingCredentials(user);

        Context context = new Context();
        context.setVariable("firstName", displayName(user));
        context.setVariable("email", user.getEmail());
        context.setVariable("supportEmail", supportEmail);

        // The one message in this flow nobody asked for, and the reason it is
        // worth sending: if the reset was not theirs, this is how they find out.
        sendAfterCommit(user.getEmail(), "Your Scripty password was changed",
                "email/password-changed", context);

        log.info("Successfully reset password for user={}", user.getUsername());
    }

    private void revokeStandingCredentials(User user) {
        // Neither is essential to the reset itself, and neither is worth losing
        // the new password over: a failure here is logged and the reset stands.
        try {
            apiTokenService.revokeAll(user.getUsername());
        } catch (RuntimeException e) {
            log.error("Failed to revoke API tokens after password reset for user={}",
                    user.getUsername(), e);
        }
        try {
            persistentTokenRepository.removeUserTokens(user.getUsername());
        } catch (RuntimeException e) {
            log.error("Failed to clear remember-me tokens after password reset for user={}",
                    user.getUsername(), e);
        }
    }

    /** Null when there is nothing worth greeting someone by. */
    private static String displayName(User user) {
        String firstName = user.getFirstName();
        return StringUtils.hasText(firstName) ? firstName.trim() : null;
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        // URL-safe and unpadded, because this spends its life in a query string.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Renders both halves of a message and sends it once the transaction that
     * asked for it has actually committed.
     *
     * <p>Mail is the one side effect a rollback cannot take back. Sent from
     * inside the transaction, a failure after the send leaves a link in someone's
     * inbox for a token that was never written — and a send that throws rolls
     * back a row the recipient may already be holding a link to. Waiting for the
     * commit makes the row the thing that decides.
     *
     * <p>Outside a transaction — a unit test, a caller that opened none — there
     * is nothing to wait for, so it goes immediately.
     */
    private void sendAfterCommit(String to, String subject, String template, Context context) {
        // Bare name for the HTML half — Boot's resolver appends .html, and the
        // layout the templates pull in is referenced the same way. The text half
        // names its extension outright, which is what MailTemplateConfig's
        // resolver keys off.
        String htmlBody = templateEngine.process(template, context);
        String textBody = templateEngine.process(template + ".txt", context);

        Runnable send = () -> emailService.send(to, subject, htmlBody, textBody, null);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Past the commit there is no transaction left to fail, so a
                // send that throws here would only escape into the caller's
                // response for work that already succeeded.
                try {
                    send.run();
                } catch (RuntimeException e) {
                    log.error("Failed to send '{}' email to={}", subject, to, e);
                }
            }
        });
    }
}
