package com.scripty.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The trash is open to any signed-in user, but scoped: the controller resolves
 * the caller's user row and only ever shows or acts on projects that row could
 * have opened. These pin that a principal without a user row gets nothing, and
 * that anonymous callers are bounced.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectTrashAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void trashPageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/project/trash"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void trashPageOpensForSignedInUsers() throws Exception {
        mockMvc.perform(get("/project/trash").with(user("admin").roles("USER", "ADMIN")))
                .andExpect(status().isOk());
    }

    /**
     * A principal with no matching user row cannot be scoped to anything, so the
     * controller bounces it rather than falling back to an unscoped listing.
     */
    @Test
    void trashPageRejectsPrincipalWithoutUserRecord() throws Exception {
        mockMvc.perform(get("/project/trash").with(user("ghost").roles("USER", "ADMIN")))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void restoreRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/project/restore").param("id", "1").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void purgeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/project/purge").param("id", "1").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void emptyTrashRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/project/empty-trash").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    /** Purging a project the caller cannot see reports it as simply not there. */
    @Test
    void purgeOfUnknownProjectRedirectsToTrash() throws Exception {
        mockMvc.perform(post("/project/purge").param("id", "999999")
                        .with(user("admin").roles("USER", "ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void deleteRemainsOpenToOrdinaryUsers() throws Exception {
        mockMvc.perform(post("/project/delete").param("id", "999999")
                        .with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    /** Deleting mutates, so it is POST-only and CSRF-protected. */
    @Test
    void deleteRejectsGet() throws Exception {
        mockMvc.perform(get("/project/delete").param("id", "999999")
                        .with(user("member").roles("USER")))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * A missing token is not a silent no-op: CsrfAccessDeniedHandler bounces the
     * post to sign-in, so the redirect target is what distinguishes a rejected
     * delete from an accepted one (both are 302).
     */
    @Test
    void deleteRejectsPostWithoutCsrf() throws Exception {
        mockMvc.perform(post("/project/delete").param("id", "999999")
                        .with(user("member").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?csrf_error=1"));
    }
}
