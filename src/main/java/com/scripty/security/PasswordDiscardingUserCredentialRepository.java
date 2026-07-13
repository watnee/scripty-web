package com.scripty.security;

import com.scripty.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/**
 * Completes deploy bootstrap when a passkey is registered. While an account is
 * still flagged {@code password_change_required} (seeded default or generated
 * one-time deploy password), saving a passkey replaces the password with a
 * random value shown to no one and clears the flag — the passkey becomes the
 * only usable credential without a manual "discard the password" step.
 *
 * <p>Accounts whose owner chose their own password (flag not set) are left
 * untouched: registering a passkey then adds a second factor, it does not take
 * the password away. Recovery for a lost passkey stays available via
 * ADMIN_PASSWORD + ADMIN_PASSWORD_RESET=true (see DefaultAdminPasswordGuard).
 */
public class PasswordDiscardingUserCredentialRepository implements UserCredentialRepository {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordDiscardingUserCredentialRepository.class);

    private final UserCredentialRepository delegate;
    private final PublicKeyCredentialUserEntityRepository userEntityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordDiscardingUserCredentialRepository(UserCredentialRepository delegate,
            PublicKeyCredentialUserEntityRepository userEntityRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.delegate = delegate;
        this.userEntityRepository = userEntityRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void delete(Bytes credentialId) {
        delegate.delete(credentialId);
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return delegate.findByCredentialId(credentialId);
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return delegate.findByUserId(userId);
    }

    @Override
    public void save(CredentialRecord credentialRecord) {
        delegate.save(credentialRecord);
        discardBootstrapPassword(credentialRecord);
    }

    private void discardBootstrapPassword(CredentialRecord credentialRecord) {
        try {
            PublicKeyCredentialUserEntity userEntity =
                    userEntityRepository.findById(credentialRecord.getUserEntityUserId());
            if (userEntity == null) {
                return;
            }
            userRepository.findByUsername(userEntity.getName()).ifPresent(user -> {
                if (!user.isPasswordChangeRequired()) {
                    return;
                }
                user.setPassword(passwordEncoder.encode(PasswordPolicy.generatePassword()));
                user.setPasswordChangeRequired(false);
                userRepository.save(user);
                log.info("Passkey registered for '{}': the temporary bootstrap password was "
                        + "replaced with a random value. The passkey is now the only usable "
                        + "credential for this account.", user.getUsername());
            });
        } catch (RuntimeException e) {
            // Never fail the passkey ceremony over bootstrap cleanup.
            log.warn("Could not discard bootstrap password after passkey registration", e);
        }
    }
}
