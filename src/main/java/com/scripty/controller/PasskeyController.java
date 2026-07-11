package com.scripty.controller;

import com.scripty.config.PasskeySettings;
import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Passkey management page at the URL Spring Security's webauthn.js expects
 * ({@code /webauthn/register}). Registration itself (POST) and deletion (DELETE
 * {@code /webauthn/register/{id}}) are handled by the framework's
 * WebAuthnRegistrationFilter before this controller; this page only renders the
 * form and the user's registered passkeys.
 */
@Controller
public class PasskeyController {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final PasskeySettings passkeySettings;
    private final PublicKeyCredentialUserEntityRepository userEntityRepository;
    private final UserCredentialRepository userCredentialRepository;

    public PasskeyController(PasskeySettings passkeySettings,
            PublicKeyCredentialUserEntityRepository userEntityRepository,
            UserCredentialRepository userCredentialRepository) {
        this.passkeySettings = passkeySettings;
        this.userEntityRepository = userEntityRepository;
        this.userCredentialRepository = userCredentialRepository;
    }

    @GetMapping("/webauthn/register")
    public String passkeys(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("passkeysEnabled", passkeySettings.isEnabled());
        model.addAttribute("passkeys", listPasskeys(principal.getName()));
        return "account/passkeys";
    }

    private List<PasskeyViewModel> listPasskeys(String username) {
        PublicKeyCredentialUserEntity userEntity = userEntityRepository.findByUsername(username);
        if (userEntity == null) {
            return List.of();
        }
        return userCredentialRepository.findByUserId(userEntity.getId()).stream()
                .map(PasskeyViewModel::of)
                .toList();
    }

    public record PasskeyViewModel(String label, String credentialId, String created,
            String lastUsed) {

        static PasskeyViewModel of(CredentialRecord record) {
            return new PasskeyViewModel(
                    record.getLabel(),
                    record.getCredentialId().toBase64UrlString(),
                    format(record.getCreated()),
                    format(record.getLastUsed()));
        }

        private static String format(Instant instant) {
            return instant == null ? "—" : DATE_FORMAT.format(instant);
        }
    }
}
