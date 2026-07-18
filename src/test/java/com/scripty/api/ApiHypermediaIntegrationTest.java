package com.scripty.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.MediaTypes;
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
                .andExpect(jsonPath("$._links.['scripty:projects'].href").exists())
                .andExpect(jsonPath("$._links.['scripty:users'].href").exists())
                .andExpect(jsonPath("$._links.self.href").exists());
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
