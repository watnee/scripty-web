package com.scripty.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Who can reach the project archive, on the web pages and over the API.
 *
 * <p>Like the trash it is open to any signed-in user but scoped to what that
 * user could have opened; unlike the trash, archiving itself is offered
 * wherever deleting is, since it is the reversible half of the same pair.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArchivedProjectAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void archivePageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/project/archived"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void archivePageOpensForSignedInUsers() throws Exception {
        mockMvc.perform(get("/project/archived").with(user("admin").roles("USER", "ADMIN")))
                .andExpect(status().isOk());
    }

    /** No user row means nothing to scope to, so the controller bounces it. */
    @Test
    void archivePageRejectsPrincipalWithoutUserRecord() throws Exception {
        mockMvc.perform(get("/project/archived").with(user("ghost").roles("USER", "ADMIN")))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void archiveRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/project/archive").param("id", "1").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void unarchiveRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/project/unarchive").param("id", "1").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    /** Archiving is open to the same ordinary users deleting is. */
    @Test
    void archiveIsOpenToOrdinaryUsers() throws Exception {
        mockMvc.perform(post("/project/archive").param("id", "999999")
                        .with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void archiveRejectsGet() throws Exception {
        mockMvc.perform(get("/project/archive").param("id", "999999")
                        .with(user("member").roles("USER")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void archiveRejectsPostWithoutCsrf() throws Exception {
        mockMvc.perform(post("/project/archive").param("id", "999999")
                        .with(user("member").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?csrf_error=1"));
    }

    /** Unarchiving something that is not archived says so rather than acting. */
    @Test
    void unarchiveOfUnknownProjectRedirectsToTheArchive() throws Exception {
        mockMvc.perform(post("/project/unarchive").param("id", "999999")
                        .with(user("admin").roles("USER", "ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/project/archived"));
    }

    @Test
    void apiArchiveCollectionRefusesAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/project/archive"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void apiArchiveCollectionOpensForSignedInUsers() throws Exception {
        mockMvc.perform(get("/api/project/archive").with(user("admin").roles("USER", "ADMIN")))
                .andExpect(status().isOk())
                // Every custom rel goes out curie-namespaced; only `self` is IANA.
                .andExpect(jsonPath("$._links.self").exists())
                .andExpect(jsonPath("$._links.['scripty:projects']").exists());
    }

    /** An id outside the caller's archive is simply not there. */
    @Test
    void apiUnarchiveOfUnknownProjectIsNotFound() throws Exception {
        mockMvc.perform(post("/api/project/archive/999999/unarchive")
                        .with(user("admin").roles("USER", "ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    /** The project collection points at the archive whether or not it is empty. */
    @Test
    void apiProjectCollectionAdvertisesTheArchive() throws Exception {
        mockMvc.perform(get("/api/project").with(user("admin").roles("USER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.['scripty:archived']").exists())
                .andExpect(jsonPath("$._links.['scripty:trash']").exists());
    }
}
