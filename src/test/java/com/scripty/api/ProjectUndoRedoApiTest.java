package com.scripty.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectUndoRedoService;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The screenplay editor's undo and redo are the only mutating transitions the
 * API advertises that live outside {@code /api}: the HAL status document points
 * at {@code /project/undo} and {@code /project/redo}. That put them outside the
 * CSRF exemption, which was written as "{@code /api/**} carrying an
 * Authorization header" — so every undo from a native client was rejected by the
 * CSRF filter before the controller ran, and the writer was told they did not
 * have permission to do that.
 *
 * <p>Pinned through the real filter chain, because the failure is in the chain
 * rather than in any code the controller tests exercise. The dev profile
 * disables CSRF outright, so a local server can never show this.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectUndoRedoApiTest {

    private static final int PROJECT_ID = 7;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectAccessSupport projectAccess;

    @MockBean
    private ProjectUndoRedoService projectUndoRedoService;

    private void givenWriter() {
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectUndoRedoService.undoWithDetails(anyInt(), any()))
                .thenReturn(new ProjectUndoRedoService.UndoRedoResult(true, false, 1));
        when(projectUndoRedoService.redoWithDetails(anyInt(), any()))
                .thenReturn(new ProjectUndoRedoService.UndoRedoResult(true, false, -1));
    }

    /**
     * A native client authenticates every request with an Authorization header
     * and keeps no CSRF token — there is no session for one to belong to.
     */
    @Test
    void undoFromANativeClientIsNotRejectedAsAForgery() throws Exception {
        givenWriter();

        mockMvc.perform(post("/project/undo").param("projectId", String.valueOf(PROJECT_ID))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .accept(MediaTypes.HAL_JSON)
                        .with(user("writer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void redoFromANativeClientIsNotRejectedAsAForgery() throws Exception {
        givenWriter();

        mockMvc.perform(post("/project/redo").param("projectId", String.valueOf(PROJECT_ID))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .accept(MediaTypes.HAL_JSON)
                        .with(user("writer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * The browser's own undo still rides on the session cookie, and a cookie is
     * exactly what a forged request can borrow — so a POST with no Authorization
     * header and no token stays rejected.
     */
    @Test
    void undoOnASessionWithoutATokenIsStillRejected() throws Exception {
        givenWriter();

        mockMvc.perform(post("/project/undo").param("projectId", String.valueOf(PROJECT_ID))
                        .accept(MediaTypes.HAL_JSON)
                        .with(user("writer").roles("USER")))
                .andExpect(status().isForbidden());
    }

    /**
     * And the cookie-borne undo the web UI actually sends — token and all —
     * still works, which is what keeps this fix from being a swap of one
     * broken client for another.
     */
    @Test
    void undoFromTheBrowserWithItsTokenStillWorks() throws Exception {
        givenWriter();

        mockMvc.perform(post("/project/undo").param("projectId", String.valueOf(PROJECT_ID))
                        .accept(MediaTypes.HAL_JSON)
                        .with(user("writer").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
