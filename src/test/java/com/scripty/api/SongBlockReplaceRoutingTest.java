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
import com.scripty.service.SongBlockService;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/song/block/bulk/replace} (Replace All) sits exactly where
 * {@code POST /api/song/block/{id}/replace} (single Replace) also matches,
 * reading {@code bulk} as the line id. The literal pattern has to win, or every
 * Replace All in a song would 400 trying to parse "bulk" as a number.
 *
 * <p>The song controller has already needed {@link SongBlockRoutingTest} for the
 * same class of collision on {@code /trash} and {@code /undo-redo-status}, and
 * the screenplay needed {@link BlockReplaceRoutingTest} for this exact pair — so
 * this is pinned before it can regress rather than after.
 *
 * <p>Pinned by what each handler does with a request the other could not have
 * accepted. The bulk call is made with a perfectly good body and is expected to
 * succeed: had it reached {@code /{id}/replace} instead, "bulk" would have
 * failed to parse as a line id and the answer would be a 400 about a type
 * mismatch rather than a 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SongBlockReplaceRoutingTest {

    private static final int BLOCK_ID = 5;
    private static final int DOCUMENT_ID = 11;
    private static final int PROJECT_ID = 9;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectAccessSupport projectAccess;

    @MockBean
    private SongBlockService songBlockService;

    @Test
    void aNumericIdReachesSingleReplace() throws Exception {
        when(songBlockService.projectIdForBlock(BLOCK_ID)).thenReturn(PROJECT_ID);
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);

        mockMvc.perform(post("/api/song/block/" + BLOCK_ID + "/replace")
                        .with(user("writer").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"find\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.find").exists());
    }

    @Test
    void bulkReplaceIsNotReadAsALineId() throws Exception {
        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(PROJECT_ID);
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);

        mockMvc.perform(post("/api/song/block/bulk/replace?documentId=" + DOCUMENT_ID)
                        .with(user("writer").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"find\":\"x\",\"replace\":\"y\"}"))
                .andExpect(status().isOk());
    }

    /** And the bulk route really does need to be told which song. */
    @Test
    void bulkReplaceWantsADocument() throws Exception {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);

        mockMvc.perform(post("/api/song/block/bulk/replace")
                        .with(user("writer").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"find\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }
}
