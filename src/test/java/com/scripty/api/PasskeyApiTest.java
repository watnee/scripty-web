package com.scripty.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.commandmodel.user.createuser.CreateUserCommandModel;
import com.scripty.service.ApiTokenService;
import com.scripty.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The native passkey ceremonies: WebAuthn reshaped for a client that keeps no
 * cookies and holds no password.
 *
 * <p>What can be pinned here is the contract around the ceremonies — the
 * challenge-id handshake, the gating, the links, and the bearer token's
 * lifecycle. The cryptographic happy path cannot run in a test (only a real
 * authenticator can sign a challenge), which is the same reason the browser
 * flow's filters aren't integration-tested either; verification itself is
 * Spring Security's, shared with the browser flow through one
 * {@code WebAuthnRelyingPartyOperations}.
 */
@SpringBootTest(properties = "app.base-url=https://scripty.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasskeyApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiTokenService apiTokens;

    @Autowired
    UserService userService;

    private final ObjectMapper json = new ObjectMapper();

    /**
     * A signed-out native client sees exactly one document — the 401
     * challenge — so passkey sign-in must be advertised there or it is not
     * discoverable at all.
     */
    @Test
    void theChallengeAdvertisesPasskeySignIn() throws Exception {
        mockMvc.perform(get("/api").accept("application/hal+json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$._links.passkeyLogin.href",
                        containsString("/api/login/passkey/options")));
    }

    @Test
    void loginOptionsAreAnonymousAndCarryTheChallengeHandle() throws Exception {
        mockMvc.perform(post("/api/login/passkey/options").accept("application/hal+json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(notNullValue()))
                .andExpect(jsonPath("$.publicKey.challenge").value(notNullValue()))
                .andExpect(jsonPath("$.publicKey.rpId").value("scripty.test"))
                .andExpect(jsonPath("$._links.['scripty:verify'].href",
                        containsString("/api/login/passkey")));
    }

    /** Single-use, minutes-lived: an unknown challenge id verifies nothing. */
    @Test
    void anUnknownCeremonyIsRefused() throws Exception {
        mockMvc.perform(post("/api/login/passkey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept("application/hal+json")
                        .content("{\"challengeId\":\"gone\",\"credential\":{"
                                + "\"id\":\"AAAA\",\"rawId\":\"AAAA\",\"type\":\"public-key\","
                                + "\"response\":{\"clientDataJSON\":\"AAAA\","
                                + "\"authenticatorData\":\"AAAA\",\"signature\":\"AAAA\"}}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationOptionsRequireSignIn() throws Exception {
        // csrf() so the answer is authorization's 401, not CSRF's 403 — an
        // anonymous native caller without either header would see the 403
        // first, but either way nothing is handed out.
        mockMvc.perform(post("/api/account/passkeys/options")
                        .accept("application/hal+json").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationOptionsNameTheSignedInUser() throws Exception {
        mockMvc.perform(post("/api/account/passkeys/options")
                        .accept("application/hal+json")
                        .with(user("pat").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(notNullValue()))
                .andExpect(jsonPath("$.publicKey.rp.id").value("scripty.test"))
                .andExpect(jsonPath("$.publicKey.user.name").value("pat"))
                .andExpect(jsonPath("$._links.['scripty:verify'].href",
                        containsString("/api/account/passkeys")));
    }

    @Test
    void thePasskeysCollectionAdvertisesRegistration() throws Exception {
        mockMvc.perform(get("/api/account/passkeys")
                        .accept("application/hal+json")
                        .with(user("pat").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.['scripty:registerPasskey'].href",
                        containsString("/api/account/passkeys/options")));
    }

    /**
     * The ceremony survives every gate short of the cryptography, and the
     * cryptography refuses garbage: a made-up attestation answers 400 rather
     * than registering, and the answer does not say which check failed.
     */
    @Test
    void aGarbageAttestationDoesNotRegister() throws Exception {
        String options = mockMvc.perform(post("/api/account/passkeys/options")
                        .accept("application/hal+json")
                        .with(user("pat").roles("USER")).with(csrf()))
                .andReturn().getResponse().getContentAsString();
        String challengeId = json.readTree(options).path("challengeId").asText();

        mockMvc.perform(post("/api/account/passkeys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept("application/hal+json")
                        .with(user("pat").roles("USER")).with(csrf())
                        .content("{\"challengeId\":\"" + challengeId + "\","
                                + "\"label\":\"iPhone\",\"credential\":{"
                                + "\"id\":\"AAAA\",\"rawId\":\"AAAA\",\"type\":\"public-key\","
                                + "\"response\":{\"clientDataJSON\":\"AAAA\","
                                + "\"attestationObject\":\"AAAA\",\"transports\":[\"internal\"]}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("could not be verified")));
    }

    /** A ceremony belongs to who started it; a label is required, like the web form's. */
    @Test
    void registrationRefusesABlankLabelAndAForeignCeremony() throws Exception {
        String options = mockMvc.perform(post("/api/account/passkeys/options")
                        .accept("application/hal+json")
                        .with(user("pat").roles("USER")).with(csrf()))
                .andReturn().getResponse().getContentAsString();
        String challengeId = json.readTree(options).path("challengeId").asText();

        String credential = "\"credential\":{\"id\":\"AAAA\",\"rawId\":\"AAAA\","
                + "\"type\":\"public-key\",\"response\":{\"clientDataJSON\":\"AAAA\","
                + "\"attestationObject\":\"AAAA\"}}";
        mockMvc.perform(post("/api/account/passkeys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept("application/hal+json")
                        .with(user("pat").roles("USER")).with(csrf())
                        .content("{\"challengeId\":\"" + challengeId + "\",\"label\":\" \","
                                + credential + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("label")));

        // Someone else echoing a stolen challenge id gets the expired answer,
        // and the attempt consumes nothing of theirs.
        mockMvc.perform(post("/api/account/passkeys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept("application/hal+json")
                        .with(user("mallory").roles("USER")).with(csrf())
                        .content("{\"challengeId\":\"" + challengeId + "\","
                                + "\"label\":\"iPhone\"," + credential + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    /**
     * iOS validates the app's webcredentials associated domain by fetching
     * this anonymously; behind the sign-in wall it might as well not exist.
     */
    @Test
    void theAppleAppSiteAssociationIsServedAnonymously() throws Exception {
        mockMvc.perform(get("/.well-known/apple-app-site-association"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webcredentials.apps[0]", containsString(".")));
    }

    /**
     * The bearer token's whole life: minted for a user, it authenticates API
     * requests in place of Basic; revoked through the sign-out endpoint, it
     * stops working immediately.
     */
    @Test
    void aBearerTokenAuthenticatesUntilRevoked() throws Exception {
        CreateUserCommandModel command = new CreateUserCommandModel();
        command.setUsername("tokenpat");
        command.setPassword("a-long-password");
        command.setFirstName("Token");
        command.setLastName("Pat");
        command.setWriter(true);
        userService.saveCreateUserCommandModel(command);

        String token = apiTokens.issue("tokenpat", "iPhone");

        mockMvc.perform(get("/api").accept("application/hal+json")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.['scripty:account'].href",
                        containsString("/api/account")));

        mockMvc.perform(delete("/api/token")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api").accept("application/hal+json")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMadeUpTokenAuthenticatesNothing() throws Exception {
        mockMvc.perform(get("/api").accept("application/hal+json")
                        .header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    /** Basic requests carry no bearer token; "sign out" must not pretend otherwise. */
    @Test
    void revokingWithoutABearerTokenIsAnError() throws Exception {
        int status = mockMvc.perform(delete("/api/token")
                        .with(user("pat").roles("USER")).with(csrf()))
                .andReturn().getResponse().getStatus();
        assertEquals(400, status);
    }
}
