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
 * {@code POST /api/block/bulk/replace} (Replace All) sits exactly where
 * {@code POST /api/block/{id}/replace} (single Replace) also matches, reading
 * {@code bulk} as the {@code id}. The literal pattern has to win, or every
 * Replace All would 400 trying to parse "bulk" as a block id.
 *
 * <p>Pinned by the validation error each handler emits on an empty body: the
 * single endpoint complains about {@code find}, the bulk one about {@code ids}.
 * A mis-route would surface neither — it would be a type-mismatch on the path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlockReplaceRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectAccessSupport projectAccess;

    @Test
    void aNumericIdReachesSingleReplace() throws Exception {
        when(projectAccess.canEditBlock(anyInt(), any(Principal.class))).thenReturn(true);

        mockMvc.perform(post("/api/block/5/replace")
                        .with(user("writer").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"find\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.find").exists());
    }

    @Test
    void bulkReplaceIsNotReadAsABlockId() throws Exception {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);

        mockMvc.perform(post("/api/block/bulk/replace")
                        .with(user("writer").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"find\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ids").exists());
    }
}
