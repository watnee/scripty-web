package com.scripty.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Recovering a deleted project is admin-only. The rest of /project/** is open to
 * ROLE_USER, so these pin that the narrower trash rules sit ahead of it in
 * SecurityConfig and do not get swallowed by the broader matcher.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectTrashAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void trashPageRequiresAdmin() throws Exception {
        mockMvc.perform(get("/project/trash"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/project/trash").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());

        // The bootstrapped admin: the controller's own check reads the admin flag
        // from the user row, so a role alone is not enough.
        mockMvc.perform(get("/project/trash").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    /**
     * ROLE_ADMIN on a principal with no matching user row must not open the trash
     * — the controller resolves the admin flag from the database.
     */
    @Test
    void trashPageRejectsAdminRoleWithoutAdminUserRecord() throws Exception {
        mockMvc.perform(get("/project/trash").with(user("ghost").roles("ADMIN")))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void restoreRequiresAdmin() throws Exception {
        mockMvc.perform(post("/project/restore").param("ids", "1").with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/project/restore").param("ids", "1")
                        .with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void restoreAllRequiresAdmin() throws Exception {
        mockMvc.perform(post("/project/restoreAll").with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/project/restoreAll")
                        .with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void permanentDeleteRequiresAdmin() throws Exception {
        mockMvc.perform(post("/project/purge").param("ids", "1").with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/project/purge").param("ids", "1")
                        .with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /** A writer can still delete; only recovery is held back to admins. */
    @Test
    void deleteRemainsOpenToOrdinaryUsers() throws Exception {
        mockMvc.perform(get("/project/delete").param("id", "999999")
                        .with(user("member").roles("USER")))
                .andExpect(status().is3xxRedirection());
    }
}
