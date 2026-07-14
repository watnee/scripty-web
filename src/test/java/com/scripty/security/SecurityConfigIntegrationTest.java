package com.scripty.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUiHtmlRestrictedToDeveloperRole() throws Exception {
        // Anonymous/Unauthorized should return redirection to login
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        // ROLE_USER should be forbidden
        mockMvc.perform(get("/swagger-ui.html").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());

        // ROLE_ADMIN should be forbidden (unless they also have ROLE_DEVELOPER)
        mockMvc.perform(get("/swagger-ui.html").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        // ROLE_DEVELOPER should not be forbidden
        mockMvc.perform(get("/swagger-ui.html").with(user("developer").roles("DEVELOPER")))
                .andExpect(status().is3xxRedirection()); // redirects to /swagger-ui/index.html
    }

    @Test
    void tokenizedScreenplayViewIsPublicButManagementIsNot() throws Exception {
        // The emailed view link must work without a session (renders the
        // invalid-link page for a bogus token, but is not gated by login).
        mockMvc.perform(get("/view").param("token", "bogus"))
                .andExpect(status().isOk());

        // Sending/revoking view invites stays behind login.
        mockMvc.perform(get("/invitation/view/send"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void unauthenticatedApiRequestGets401ChallengeNotLoginRedirect() throws Exception {
        // Native clients (SwiftUI) must see a Basic challenge, not the login page.
        mockMvc.perform(get("/api").accept("application/hal+json"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"Scripty API\""));
    }

    @Test
    void apiWriteWithAuthorizationHeaderSkipsCsrf() throws Exception {
        // Native clients authenticate per-request and never hold a CSRF token.
        // The exemption keys on the Authorization header's presence (a Bearer
        // scheme here so the Basic filter doesn't consume it), so this must reach
        // controller validation (400 for the empty body) instead of dying at 403.
        mockMvc.perform(post("/api/project")
                        .with(user("user").roles("USER"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiWriteWithoutAuthorizationHeaderStillRequiresCsrf() throws Exception {
        // Cookie-authenticated /api calls keep CSRF protection (JSON Accept gets
        // a plain 403; HTML-accepting requests get redirected to /login?csrf_error=1).
        mockMvc.perform(post("/api/project")
                        .with(user("user").roles("USER"))
                        .accept("application/hal+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
