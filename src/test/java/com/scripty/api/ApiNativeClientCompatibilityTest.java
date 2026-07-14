package com.scripty.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The wire-format contract a native client (SwiftUI + Decodable) relies on:
 * collections always carry {@code _embedded} under stable rel-named keys,
 * plain {@code application/json} is accepted, and every error status returns
 * the {@link ApiError} envelope instead of an empty body or Spring's default
 * error shape.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiNativeClientCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void collectionsRenderEmbeddedUnderStableRelName() throws Exception {
        // Even when the list is empty, _embedded.projects must be present (as [])
        // so a Decodable with a non-optional _embedded property can parse it.
        mockMvc.perform(get("/api/project").with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.projects").isArray());
    }

    @Test
    void apiAcceptsPlainApplicationJson() throws Exception {
        // Swift clients routinely set Accept: application/json explicitly;
        // that must not 406 just because responses are HAL-shaped.
        mockMvc.perform(get("/api")
                        .with(user("user").roles("USER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/project")
                        .with(user("user").roles("USER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void validationFailureUsesErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/project")
                        .with(user("user").roles("USER"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors.title").isNotEmpty());
    }

    @Test
    void malformedJsonUsesErrorEnvelopeInsteadOfSpringDefault() throws Exception {
        mockMvc.perform(post("/api/project")
                        .with(user("user").roles("USER"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed_request"));
    }

    @Test
    void accessDenialCarriesJsonBodyNotEmptyResponse() throws Exception {
        mockMvc.perform(get("/api/project/999999").with(user("user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void roleDenialAtSecurityFilterCarriesJsonBody() throws Exception {
        // /api/user/** requires ADMIN; the filter-level denial must still be JSON.
        mockMvc.perform(get("/api/user").with(user("user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void csrfFailureOnCookieAuthedApiCallExplainsItselfInJson() throws Exception {
        mockMvc.perform(post("/api/project")
                        .with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("csrf_required"));
    }

    @Test
    void missingRequiredParameterUsesErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/block").with(user("user").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing_parameter"));
    }
}
