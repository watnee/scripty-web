package com.scripty.controller;

import com.scripty.api.AccountResource;
import com.scripty.api.ApiRel;
import com.scripty.api.ChangePasswordRequest;
import com.scripty.api.PasskeyResource;
import com.scripty.config.PasskeySettings;
import com.scripty.dto.User;
import com.scripty.service.UserService;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * The signed-in user's own account, over the API: change your password, and see
 * or revoke the passkeys registered to you.
 *
 * <p>Everything here acts on the principal, never on an id supplied by the
 * caller — there is no way to name someone else's account, which is what keeps
 * these endpoints out of the admin block in the security config. Registering a
 * new passkey is deliberately absent: it is a WebAuthn ceremony the browser and
 * Spring Security's filters run between them, not something a REST call can do.
 */
@RestController
@RequestMapping(value = "/api/account")
public class AccountRestController {

    private final UserService userService;
    private final PasskeySettings passkeySettings;
    private final PublicKeyCredentialUserEntityRepository userEntityRepository;
    private final UserCredentialRepository userCredentialRepository;

    public AccountRestController(UserService userService,
            PasskeySettings passkeySettings,
            PublicKeyCredentialUserEntityRepository userEntityRepository,
            UserCredentialRepository userCredentialRepository) {
        this.userService = userService;
        this.passkeySettings = passkeySettings;
        this.userEntityRepository = userEntityRepository;
        this.userCredentialRepository = userCredentialRepository;
    }

    @GetMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<AccountResource>> show(Principal principal) {
        User user = currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(toModel(user));
    }

    @RequestMapping(value = "/password", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request,
            Principal principal) {
        User user = currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request == null || request.newPassword() == null || request.newPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "New password is required."));
        }
        try {
            userService.changePassword(
                    principal.getName(), request.currentPassword(), request.newPassword());
        } catch (IllegalArgumentException e) {
            // Wrong current password, too weak, unchanged — the message is the
            // point, so it is passed through rather than flattened to a status.
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        return ResponseEntity.ok(toModel(currentUser(principal)));
    }

    @GetMapping(value = "/passkeys", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<CollectionModel<EntityModel<PasskeyResource>>> passkeys(Principal principal) {
        if (!passkeySettings.isEnabled() || principal == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(passkeyCollection(principal.getName()));
    }

    /**
     * Revokes one of the caller's own passkeys. The id is matched against the
     * credentials registered to this user, so naming someone else's passkey
     * answers 404 rather than deleting it.
     */
    @RequestMapping(value = "/passkeys/{credentialId}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> deletePasskey(@PathVariable String credentialId, Principal principal) {
        if (!passkeySettings.isEnabled() || principal == null) {
            return ResponseEntity.notFound().build();
        }
        CredentialRecord owned = findOwnCredential(principal.getName(), credentialId);
        if (owned == null) {
            return ResponseEntity.notFound().build();
        }
        userCredentialRepository.delete(owned.getCredentialId());
        return ResponseEntity.ok(passkeyCollection(principal.getName()));
    }

    // MARK: - helpers

    private User currentUser(Principal principal) {
        return principal == null ? null : userService.readByUsername(principal.getName());
    }

    private EntityModel<AccountResource> toModel(User user) {
        AccountResource resource = new AccountResource();
        if (user != null) {
            resource.setUsername(user.getUsername());
            resource.setFirstName(user.getFirstName());
            resource.setLastName(user.getLastName());
            resource.setPasswordChangeRequired(user.isPasswordChangeRequired());
        }
        resource.setPasskeysEnabled(passkeySettings.isEnabled());

        EntityModel<AccountResource> model = EntityModel.of(resource,
                linkTo(methodOn(AccountRestController.class).show(null)).withSelfRel(),
                linkTo(methodOn(AccountRestController.class).changePassword(null, null))
                        .withRel(ApiRel.CHANGE_PASSWORD));
        // Only where passkeys are configured: advertising the rel otherwise hands
        // a client a link that can only 404.
        if (passkeySettings.isEnabled()) {
            model.add(linkTo(methodOn(AccountRestController.class).passkeys(null))
                    .withRel(ApiRel.PASSKEYS));
        }
        return model;
    }

    private CredentialRecord findOwnCredential(String username, String credentialId) {
        for (CredentialRecord record : ownCredentials(username)) {
            if (record.getCredentialId() != null
                    && record.getCredentialId().toBase64UrlString().equals(credentialId)) {
                return record;
            }
        }
        return null;
    }

    private List<CredentialRecord> ownCredentials(String username) {
        PublicKeyCredentialUserEntity userEntity = userEntityRepository.findByUsername(username);
        if (userEntity == null) {
            return List.of();
        }
        return userCredentialRepository.findByUserId(userEntity.getId());
    }

    private CollectionModel<EntityModel<PasskeyResource>> passkeyCollection(String username) {
        List<EntityModel<PasskeyResource>> items = new ArrayList<>();
        for (CredentialRecord record : ownCredentials(username)) {
            items.add(toModel(record));
        }
        return CollectionModel.of(items)
                .add(linkTo(methodOn(AccountRestController.class).passkeys(null)).withSelfRel(),
                        linkTo(methodOn(AccountRestController.class).show(null)).withRel(ApiRel.ACCOUNT));
    }

    private EntityModel<PasskeyResource> toModel(CredentialRecord record) {
        PasskeyResource resource = new PasskeyResource();
        String id = record.getCredentialId() == null
                ? null
                : record.getCredentialId().toBase64UrlString();
        resource.setCredentialId(id);
        resource.setLabel(record.getLabel());
        resource.setCreated(format(record.getCreated()));
        resource.setLastUsed(format(record.getLastUsed()));
        return EntityModel.of(resource,
                linkTo(methodOn(AccountRestController.class).deletePasskey(id, null))
                        .withRel(ApiRel.DELETE),
                linkTo(methodOn(AccountRestController.class).passkeys(null))
                        .withRel(ApiRel.PASSKEYS));
    }

    /**
     * Whole seconds with an explicit offset, for the same reason {@link
     * com.scripty.api.ApiDates} truncates: Swift's default ISO-8601 decoding
     * rejects fractional seconds.
     */
    private String format(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
