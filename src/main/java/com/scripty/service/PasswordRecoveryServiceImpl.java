package com.scripty.service;

import com.scripty.dto.PasswordRecoveryToken;
import com.scripty.dto.User;
import com.scripty.repository.PasswordRecoveryTokenRepository;
import com.scripty.repository.UserRepository;
import com.scripty.security.PasswordPolicy;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordRecoveryTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Autowired
    public PasswordRecoveryServiceImpl(UserRepository userRepository,
                                       PasswordRecoveryTokenRepository tokenRepository,
                                       EmailService emailService,
                                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
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

        // Generate token and expiry (2 hours)
        String tokenString = UUID.randomUUID().toString();
        PasswordRecoveryToken token = new PasswordRecoveryToken();
        token.setUser(user);
        token.setToken(tokenString);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusHours(2));

        tokenRepository.save(token);

        // Send email
        String resetUrl = baseUrl + "/forgot-password/reset?token=" + tokenString;
        String subject = "Reset your Scripty password";
        String htmlBody = String.format(
                "<div style=\"font-family: sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; line-height: 1.6;\">"
                        + "<h2>Password Reset Request</h2>"
                        + "<p>Hello %s,</p>"
                        + "<p>We received a request to reset the password for your Scripty account associated with this email address.</p>"
                        + "<p>Click the button below to choose a new password. This link is valid for 2 hours.</p>"
                        + "<div style=\"margin: 30px 0;\">"
                        + "  <a href=\"%s\" style=\"background-color: #3878a8; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; font-weight: bold; display: inline-block;\">Reset Password</a>"
                        + "</div>"
                        + "<p>If the button doesn't work, you can copy and paste this link into your browser:</p>"
                        + "<p><a href=\"%s\">%s</a></p>"
                        + "<p>If you did not request a password reset, you can safely ignore this email.</p>"
                        + "<hr style=\"border: none; border-top: 1px solid #eee; margin-top: 30px;\" />"
                        + "<p style=\"font-size: 0.8em; color: #888;\">Scripty App</p>"
                        + "</div>",
                user.getFirstName(), resetUrl, resetUrl, resetUrl
        );

        log.info("Sending password recovery email to={} token={}", user.getEmail(), tokenString);
        emailService.send(user.getEmail(), subject, htmlBody);
    }

    @Override
    @Transactional(readOnly = true)
    public PasswordRecoveryToken validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token must not be empty.");
        }

        PasswordRecoveryToken recoveryToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token."));

        if (recoveryToken.isExpired()) {
            throw new IllegalArgumentException("The password reset token has expired.");
        }

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

        log.info("Successfully reset password for user={}", user.getUsername());
    }
}
