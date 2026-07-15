package com.scripty.controller;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON view of the signed-in user's registered passkeys, for the native client's
 * account screen. Registration and deletion themselves go through Spring
 * Security's own filters ({@code POST /webauthn/register},
 * {@code DELETE /webauthn/register/{id}}); this endpoint only lists.
 */
@RestController
public class PasskeyApiController {

    private final PublicKeyCredentialUserEntityRepository userEntityRepository;
    private final UserCredentialRepository userCredentialRepository;

    public PasskeyApiController(PublicKeyCredentialUserEntityRepository userEntityRepository,
            UserCredentialRepository userCredentialRepository) {
        this.userEntityRepository = userEntityRepository;
        this.userCredentialRepository = userCredentialRepository;
    }

    @GetMapping("/api/passkeys")
    public ResponseEntity<List<PasskeyDto>> passkeys(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        PublicKeyCredentialUserEntity userEntity =
                userEntityRepository.findByUsername(principal.getName());
        if (userEntity == null) {
            return ResponseEntity.ok(List.of());
        }
        List<PasskeyDto> passkeys = userCredentialRepository.findByUserId(userEntity.getId()).stream()
                .map(PasskeyDto::of)
                .toList();
        return ResponseEntity.ok(passkeys);
    }

    public record PasskeyDto(String id, String label, Instant created, Instant lastUsed) {

        static PasskeyDto of(CredentialRecord record) {
            return new PasskeyDto(
                    record.getCredentialId().toBase64UrlString(),
                    record.getLabel(),
                    record.getCreated(),
                    record.getLastUsed());
        }
    }
}
