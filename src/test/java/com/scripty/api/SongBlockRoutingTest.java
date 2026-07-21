package com.scripty.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongUndoRedoService;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code /api/song/block/trash} and {@code /api/song/block/undo-redo-status}
 * sit exactly where {@code /api/song/block/{id}} also matches, so both would be
 * read as a line id if the literal patterns did not win.
 *
 * <p>Worth pinning through the real dispatcher rather than by calling the
 * controller methods: the failure is in the routing, not the code, and it looks
 * like a 400 about an unparseable id rather than anything named.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SongBlockRoutingTest {

    private static final int DOCUMENT_ID = 5;
    private static final int PROJECT_ID = 9;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectAccessSupport projectAccess;

    @MockBean
    private SongBlockService songBlockService;

    @MockBean
    private SongUndoRedoService songUndoRedoService;

    private void givenWriter() {
        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(PROJECT_ID);
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);
    }

    @Test
    void theTrashIsNotReadAsALineId() throws Exception {
        givenWriter();

        mockMvc.perform(get("/api/song/block/trash?documentId=" + DOCUMENT_ID)
                        .accept(MediaTypes.HAL_JSON).with(user("writer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.['scripty:songBlocks'].href").exists());
    }

    @Test
    void theUndoStatusIsNotReadAsALineId() throws Exception {
        givenWriter();
        when(songUndoRedoService.canUndo(anyInt(), any())).thenReturn(true);
        when(songUndoRedoService.canRedo(anyInt(), any())).thenReturn(false);

        mockMvc.perform(get("/api/song/block/undo-redo-status?documentId=" + DOCUMENT_ID)
                        .accept(MediaTypes.HAL_JSON).with(user("writer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canUndo").value(true))
                .andExpect(jsonPath("$.canRedo").value(false))
                .andExpect(jsonPath("$._links.['scripty:undo'].href").exists());
    }
}
