package com.scripty.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.User;
import com.scripty.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

class PasswordDiscardingUserCredentialRepositoryTest {

    private UserCredentialRepository delegate;
    private PublicKeyCredentialUserEntityRepository userEntityRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private PasswordDiscardingUserCredentialRepository repository;

    private final Bytes userHandle = Bytes.random();

    @BeforeEach
    void setUp() {
        delegate = mock(UserCredentialRepository.class);
        userEntityRepository = mock(PublicKeyCredentialUserEntityRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        repository = new PasswordDiscardingUserCredentialRepository(
                delegate, userEntityRepository, userRepository, passwordEncoder);
    }

    @Test
    void savingPasskeyDiscardsBootstrapPasswordAndClearsFlag() {
        User admin = user("admin", passwordEncoder.encode("temporary-bootstrap-pw"), true);
        wireUser("admin", admin);

        repository.save(credentialRecord());

        verify(delegate).save(any(CredentialRecord.class));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertFalse(saved.getValue().isPasswordChangeRequired());
        assertFalse(passwordEncoder.matches("temporary-bootstrap-pw", saved.getValue().getPassword()));
    }

    @Test
    void savingPasskeyKeepsOwnerChosenPassword() {
        String ownHash = passwordEncoder.encode("owner-chosen-password");
        User admin = user("admin", ownHash, false);
        wireUser("admin", admin);

        repository.save(credentialRecord());

        verify(delegate).save(any(CredentialRecord.class));
        verify(userRepository, never()).save(any(User.class));
        assertTrue(passwordEncoder.matches("owner-chosen-password", admin.getPassword()));
    }

    @Test
    void savingPasskeyForUnknownUserEntityStillPersistsCredential() {
        when(userEntityRepository.findById(userHandle)).thenReturn(null);

        repository.save(credentialRecord());

        verify(delegate).save(any(CredentialRecord.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void discardFailureDoesNotFailPasskeyCeremony() {
        when(userEntityRepository.findById(userHandle)).thenThrow(new RuntimeException("db down"));

        repository.save(credentialRecord());

        verify(delegate).save(any(CredentialRecord.class));
    }

    private void wireUser(String username, User user) {
        PublicKeyCredentialUserEntity entity = mock(PublicKeyCredentialUserEntity.class);
        when(entity.getName()).thenReturn(username);
        when(userEntityRepository.findById(userHandle)).thenReturn(entity);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
    }

    private CredentialRecord credentialRecord() {
        CredentialRecord record = mock(CredentialRecord.class);
        when(record.getUserEntityUserId()).thenReturn(userHandle);
        return record;
    }

    private static User user(String username, String passwordHash, boolean changeRequired) {
        User user = new User();
        user.setId(1);
        user.setUsername(username);
        user.setPassword(passwordHash);
        user.setEnabled(true);
        user.setPasswordChangeRequired(changeRequired);
        return user;
    }
}
