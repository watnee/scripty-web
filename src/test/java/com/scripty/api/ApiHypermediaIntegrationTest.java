package com.scripty.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end checks on how the {@code /api} surface renders hypermedia: the
 * curie provider namespaces the custom relations and points at their docs, and
 * HAL-FORMS is negotiable alongside HAL. Booting the full context here also
 * proves {@link com.scripty.config.HypermediaConfig} wires up cleanly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiHypermediaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootDocumentNamespacesCustomRelsUnderAScriptyCurie() throws Exception {
        mockMvc.perform(get("/api").accept(MediaTypes.HAL_JSON).with(user("member").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_JSON))
                // The curie itself: named, templated, and pointing at the rel docs.
                .andExpect(jsonPath("$._links.curies[0].name").value("scripty"))
                .andExpect(jsonPath("$._links.curies[0].templated").value(true))
                .andExpect(jsonPath("$._links.curies[0].href",
                        org.hamcrest.Matchers.containsString("/docs/api-rels.html")))
                // Custom rels come out namespaced; IANA self stays bare.
                // `actors` stands in for what `users` used to demonstrate here:
                // users and teams are advertised only to admins now, so they
                // are no longer a fair example of unconditional namespacing.
                .andExpect(jsonPath("$._links.['scripty:projects'].href").exists())
                .andExpect(jsonPath("$._links.['scripty:actors'].href").exists())
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    /**
     * {@code /api/user/**} and {@code /api/team/**} are admin-only in the
     * security config, so advertising them to everyone handed most callers two
     * links that could only 403. A hypermedia client is entitled to treat an
     * advertised rel as one it may follow.
     */
    @Test
    void rootWithholdsAdminOnlyRelsFromOrdinaryMembers() throws Exception {
        mockMvc.perform(get("/api").accept(MediaTypes.HAL_JSON).with(user("member").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.['scripty:users']").doesNotExist())
                .andExpect(jsonPath("$._links.['scripty:teams']").doesNotExist())
                // The rest of the surface is unaffected.
                .andExpect(jsonPath("$._links.['scripty:projects'].href").exists());
    }

    @Test
    void rootDocumentAdvertisesStoredPreferences() throws Exception {
        // Preferences used to be reachable only by a client that already knew
        // the path; they are part of the discoverable surface now.
        mockMvc.perform(get("/api").accept(MediaTypes.HAL_JSON).with(user("member").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.['scripty:capitalizationPreferences'].href",
                        org.hamcrest.Matchers.containsString("/api/preferences/capitalization")));
    }

    @Test
    void capitalizationPreferencesCarryLinksAndAnUpdateAffordance() throws Exception {
        mockMvc.perform(get("/api/preferences/capitalization")
                        .accept(MediaTypes.HAL_FORMS_JSON).with(user("member").roles("USER")))
                .andExpect(status().isOk())
                // The stored flags stay top-level, so existing clients reading
                // them by name are unaffected by the HAL wrapper.
                .andExpect(jsonPath("$.scene").exists())
                .andExpect(jsonPath("$.character").exists())
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.['scripty:update'].href").exists())
                // HAL-FORMS renders the POST affordance as a template.
                .andExpect(jsonPath("$._templates").exists());
    }

    @Test
    void preferencesStayBehindAuthentication() throws Exception {
        // Both handlers dereference the principal without a null check, so this
        // rule is load-bearing rather than belt-and-braces: if the security
        // config ever let an anonymous request through, they would NPE.
        mockMvc.perform(get("/api/preferences/capitalization").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/preferences/capitalization").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":false}")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rootDocumentIsAlsoServableAsHalForms() throws Exception {
        // Proves HAL-FORMS is enabled (not Spring Boot's HAL-only default), which
        // is what lets the assemblers' affordances surface as _templates.
        mockMvc.perform(get("/api").accept(MediaTypes.HAL_FORMS_JSON).with(user("member").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaTypes.HAL_FORMS_JSON));
    }
}
