package com.scripty.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.api.ApiRel;
import com.scripty.api.PasskeyCeremonyRequests.PasskeyAssertionRequest;
import com.scripty.api.PasskeyCeremonyRequests.PasskeyCredentialPayload;
import com.scripty.api.PasskeyCeremonyRequests.PasskeyRegistrationRequest;
import com.scripty.api.PasskeyOptionsResource;
import com.scripty.api.PasskeySessionResource;
import com.scripty.config.PasskeySettings;
import com.scripty.security.PasskeyCeremonyStore;
import com.scripty.service.ApiTokenService;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.jackson.WebauthnJackson2Module;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * The WebAuthn ceremonies for native clients: registering a passkey while
 * signed in, and signing in with one.
 *
 * <p>The browser runs these ceremonies against Spring Security's own filters,
 * which correlate the two halves through the HTTP session and finish by
 * writing a session cookie — none of which a cookie-less Basic-auth client
 * can use. These endpoints run the same {@link WebAuthnRelyingPartyOperations}
 * (same relying party, same credential tables, same verification) but carry
 * the ceremony state in a {@code challengeId} and finish a sign-in by minting
 * a bearer token, because a passkey leaves the client with no password to put
 * in a Basic header.
 */
@RestController
public class PasskeyRestController {

    /**
     * Spring Security's Jackson module renders the options documents in the
     * exact W3C wire shape the browser flow uses (base64url byte fields, DOM
     * string enums), so both flows share one contract.
     */
    private static final ObjectMapper WEBAUTHN_JSON =
            new ObjectMapper().registerModule(new WebauthnJackson2Module());

    private final PasskeySettings settings;
    private final WebAuthnRelyingPartyOperations operations;
    private final PasskeyCeremonyStore ceremonies;
    private final ApiTokenService tokens;
    private final UserDetailsService userDetailsService;
    private final AccountRestController account;

    public PasskeyRestController(PasskeySettings settings,
            WebAuthnRelyingPartyOperations operations,
            PasskeyCeremonyStore ceremonies,
            ApiTokenService tokens,
            UserDetailsService userDetailsService,
            AccountRestController account) {
        this.settings = settings;
        this.operations = operations;
        this.ceremonies = ceremonies;
        this.tokens = tokens;
        this.userDetailsService = userDetailsService;
        this.account = account;
    }

    // MARK: - Registration (signed in)

    @RequestMapping(value = "/api/account/passkeys/options", method = RequestMethod.POST,
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> registrationOptions(Principal principal) {
        if (!settings.isEnabled() || principal == null) {
            return ResponseEntity.notFound().build();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var options = operations.createPublicKeyCredentialCreationOptions(
                new ImmutablePublicKeyCredentialCreationOptionsRequest(authentication));
        PasskeyOptionsResource resource = new PasskeyOptionsResource();
        resource.setChallengeId(ceremonies.putRegistration(principal.getName(), options));
        resource.setPublicKey(WEBAUTHN_JSON.valueToTree(options));
        return ResponseEntity.ok(EntityModel.of(resource,
                linkTo(methodOn(PasskeyRestController.class).registerPasskey(null, null))
                        .withRel(ApiRel.VERIFY)));
    }

    @RequestMapping(value = "/api/account/passkeys", method = RequestMethod.POST,
            consumes = "application/json",
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> registerPasskey(@RequestBody PasskeyRegistrationRequest request,
            Principal principal) {
        if (!settings.isEnabled() || principal == null) {
            return ResponseEntity.notFound().build();
        }
        if (request == null || request.credential() == null
                || request.credential().response() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Passkey data is required."));
        }
        if (request.label() == null || request.label().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "A passkey label is required."));
        }
        PasskeyCeremonyStore.RegistrationCeremony ceremony =
                ceremonies.takeRegistration(request.challengeId());
        if (ceremony == null || !ceremony.username().equals(principal.getName())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "The passkey ceremony has expired. Try again."));
        }
        try {
            operations.registerCredential(new ImmutableRelyingPartyRegistrationRequest(
                    ceremony.options(),
                    new RelyingPartyPublicKey(toAttestation(request.credential()),
                            request.label().trim())));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "The passkey could not be verified."));
        }
        return ResponseEntity.ok(account.passkeyCollectionFor(principal.getName()));
    }

    // MARK: - Sign-in (anonymous)

    @RequestMapping(value = "/api/login/passkey/options", method = RequestMethod.POST,
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> loginOptions() {
        if (!settings.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        // The caller is anonymous, so the options carry no allowCredentials:
        // this is the discoverable-credential flow, where the authenticator
        // itself offers the accounts it holds passkeys for.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var options = operations.createCredentialRequestOptions(
                new ImmutablePublicKeyCredentialRequestOptionsRequest(authentication));
        PasskeyOptionsResource resource = new PasskeyOptionsResource();
        resource.setChallengeId(ceremonies.putAssertion(options));
        resource.setPublicKey(WEBAUTHN_JSON.valueToTree(options));
        return ResponseEntity.ok(EntityModel.of(resource,
                linkTo(methodOn(PasskeyRestController.class).loginWithPasskey(null))
                        .withRel(ApiRel.VERIFY)));
    }

    @RequestMapping(value = "/api/login/passkey", method = RequestMethod.POST,
            consumes = "application/json",
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> loginWithPasskey(@RequestBody PasskeyAssertionRequest request) {
        if (!settings.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        if (request == null || request.credential() == null
                || request.credential().response() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Passkey data is required."));
        }
        PasskeyCeremonyStore.AssertionCeremony ceremony =
                ceremonies.takeAssertion(request.challengeId());
        if (ceremony == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "The passkey ceremony has expired. Try again."));
        }
        String username;
        try {
            PublicKeyCredentialUserEntity userEntity = operations.authenticate(
                    new RelyingPartyAuthenticationRequest(ceremony.options(),
                            toAssertion(request.credential())));
            username = userEntity == null ? null : userEntity.getName();
        } catch (RuntimeException e) {
            username = null;
        }
        // One failure answer on purpose: which part failed (signature, unknown
        // credential, disabled account) is not something to teach a caller.
        UserDetails user = username == null ? null : findEnabledUser(username);
        if (user == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Passkey sign-in failed."));
        }
        PasskeySessionResource resource = new PasskeySessionResource();
        resource.setUsername(username);
        resource.setToken(tokens.issue(username, request.label()));
        return ResponseEntity.ok(EntityModel.of(resource,
                linkTo(methodOn(PasskeyRestController.class).revokeToken(null))
                        .withRel(ApiRel.REVOKE_TOKEN)));
    }

    /**
     * The API's sign-out: revokes the bearer token that authenticated this
     * very request. Basic requests have nothing here to revoke, so they are
     * answered 400 rather than silently succeeding.
     */
    @RequestMapping(value = "/api/token", method = RequestMethod.DELETE)
    public ResponseEntity<?> revokeToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Only bearer tokens can be revoked."));
        }
        tokens.revoke(header.substring("Bearer ".length()).trim());
        return ResponseEntity.noContent().build();
    }

    // MARK: - helpers

    private UserDetails findEnabledUser(String username) {
        try {
            UserDetails user = userDetailsService.loadUserByUsername(username);
            return user.isEnabled() ? user : null;
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    private static PublicKeyCredential<AuthenticatorAttestationResponse> toAttestation(
            PasskeyCredentialPayload payload) {
        return PublicKeyCredential.<AuthenticatorAttestationResponse>builder()
                .id(payload.id())
                .rawId(Bytes.fromBase64(payload.rawId()))
                .type(PublicKeyCredentialType.PUBLIC_KEY)
                .response(AuthenticatorAttestationResponse.builder()
                        .attestationObject(Bytes.fromBase64(payload.response().attestationObject()))
                        .clientDataJSON(Bytes.fromBase64(payload.response().clientDataJSON()))
                        .transports(toTransports(payload.response().transports()))
                        .build())
                .build();
    }

    private static PublicKeyCredential<AuthenticatorAssertionResponse> toAssertion(
            PasskeyCredentialPayload payload) {
        Bytes userHandle = payload.response().userHandle() == null
                ? null
                : Bytes.fromBase64(payload.response().userHandle());
        return PublicKeyCredential.<AuthenticatorAssertionResponse>builder()
                .id(payload.id())
                .rawId(Bytes.fromBase64(payload.rawId()))
                .type(PublicKeyCredentialType.PUBLIC_KEY)
                .response(AuthenticatorAssertionResponse.builder()
                        .authenticatorData(Bytes.fromBase64(payload.response().authenticatorData()))
                        .clientDataJSON(Bytes.fromBase64(payload.response().clientDataJSON()))
                        .signature(Bytes.fromBase64(payload.response().signature()))
                        .userHandle(userHandle)
                        .build())
                .build();
    }

    private static List<AuthenticatorTransport> toTransports(List<String> names) {
        List<AuthenticatorTransport> transports = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                transports.add(AuthenticatorTransport.valueOf(name));
            }
        }
        return transports;
    }
}
