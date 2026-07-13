package com.scripty.bootstrap;

import com.scripty.dto.User;
import com.scripty.repository.UserRepository;
import com.scripty.security.PasswordPolicy;
import com.scripty.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Deployment credential guard (non-dev profiles). Historically the app seeded
 * an {@code admin}/{@code admin} account (V23 migration), which meant a fresh
 * deploy was reachable with well-known credentials. On every startup this guard:
 *
 * <ul>
 *   <li>rotates the deploy admin's password when it is still a well-known default —
 *       to {@code ADMIN_PASSWORD} when that is a strong secret, otherwise to a
 *       generated one-time password printed once to the log;</li>
 *   <li>flags any other enabled account still on a well-known password so it must
 *       be changed at next login (see ForcedPasswordChangeFilter);</li>
 *   <li>creates the admin account with a generated password when the database has
 *       no users and no ADMIN_USERNAME/ADMIN_PASSWORD were supplied, so a fresh
 *       deploy is never left without a usable (but safe) login;</li>
 *   <li>supports break-glass recovery: setting {@code ADMIN_PASSWORD_RESET=true}
 *       together with a strong {@code ADMIN_PASSWORD} force-resets the admin
 *       password on boot, for when the admin password is lost.</li>
 * </ul>
 */
@Component
@Profile("!dev")
@Order(20)
public class DefaultAdminPasswordGuard implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminPasswordGuard.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_USERNAME:admin}")
    private String adminUsername;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @Value("${ADMIN_PASSWORD_RESET:false}")
    private boolean adminPasswordReset;

    public DefaultAdminPasswordGuard(UserRepository userRepository, UserService userService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (adminPasswordReset) {
            breakGlassReset();
        }
        if (userRepository.count() == 0) {
            createAdminWithGeneratedPassword();
            return;
        }
        for (User user : userRepository.findAllByOrderByUsernameAsc()) {
            if (!user.isEnabled()) {
                continue;
            }
            if (!PasswordPolicy.matchesWellKnownPassword(
                    passwordEncoder, user.getPassword(), user.getUsername())) {
                continue;
            }
            if (user.getUsername().equalsIgnoreCase(adminUsername)) {
                rotateAdminPassword(user);
            } else {
                user.setPasswordChangeRequired(true);
                userRepository.save(user);
                log.warn("User '{}' still has a well-known default password; "
                        + "a password change will be required at next login.", user.getUsername());
            }
        }
    }

    private void breakGlassReset() {
        if (!PasswordPolicy.isStrongDeployPassword(adminPassword, adminUsername)) {
            log.error("ADMIN_PASSWORD_RESET is set but ADMIN_PASSWORD is missing or too weak "
                    + "(minimum {} characters, not a common password) — reset skipped.",
                    PasswordPolicy.MIN_DEPLOY_LENGTH);
            return;
        }
        userRepository.findByUsername(adminUsername).ifPresentOrElse(user -> {
            user.setPassword(passwordEncoder.encode(adminPassword));
            user.setPasswordChangeRequired(false);
            userRepository.save(user);
            log.warn("ADMIN_PASSWORD_RESET: password for '{}' was reset from ADMIN_PASSWORD. "
                    + "Remove ADMIN_PASSWORD_RESET (and ideally ADMIN_PASSWORD) from the "
                    + "environment now.", adminUsername);
        }, () -> log.error("ADMIN_PASSWORD_RESET is set but user '{}' does not exist.",
                adminUsername));
    }

    private void createAdminWithGeneratedPassword() {
        String generated = PasswordPolicy.generatePassword();
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(generated);
        admin.setEnabled(true);
        admin.setAdmin(true);
        admin.setFirstName("Admin");
        admin.setLastName("User");
        userService.create(admin);
        flagPasswordChangeRequired(adminUsername);
        logGeneratedPassword(adminUsername, generated);
    }

    private void rotateAdminPassword(User admin) {
        if (PasswordPolicy.isStrongDeployPassword(adminPassword, adminUsername)) {
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setPasswordChangeRequired(false);
            userRepository.save(admin);
            log.warn("Admin account '{}' had a well-known default password; it was reset from "
                    + "ADMIN_PASSWORD.", admin.getUsername());
            return;
        }
        String generated = PasswordPolicy.generatePassword();
        admin.setPassword(passwordEncoder.encode(generated));
        admin.setPasswordChangeRequired(true);
        userRepository.save(admin);
        logGeneratedPassword(admin.getUsername(), generated);
    }

    private void flagPasswordChangeRequired(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setPasswordChangeRequired(true);
            userRepository.save(user);
        });
    }

    private void logGeneratedPassword(String username, String generated) {
        // Deliberately Jenkins-style: the one-time password is printed to the deploy
        // log because there is no other channel on a fresh deploy. It stops working
        // as soon as the owner logs in, because a password change is forced.
        log.warn("\n"
                + "*************************************************************\n"
                + "* Admin credentials were rotated because the previous       *\n"
                + "* password was a well-known default.                        *\n"
                + "*                                                           *\n"
                + "* Username: {}\n"
                + "* One-time password: {}\n"
                + "*                                                           *\n"
                + "* At first login, either register a passkey (recommended:   *\n"
                + "* this discards the password automatically) or set a new    *\n"
                + "* password. To choose the password yourself, set a strong   *\n"
                + "* ADMIN_PASSWORD env var ({}+ chars) and redeploy.          *\n"
                + "*************************************************************",
                username, generated, PasswordPolicy.MIN_DEPLOY_LENGTH);
    }
}
