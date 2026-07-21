package com.scripty.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.BlockCommentService;
import java.security.Principal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The wire shape of the comment counts, asserted through the real HAL
 * serializer rather than on the model object.
 *
 * <p>Worth its own test because the payload is an {@code EntityModel} wrapping
 * a map, and the client reads {@code counts} as a top-level field. If the HAL
 * wrapper ever nested it — under {@code content}, say — nothing would throw:
 * the client would decode an empty map and simply stop drawing badges, which
 * looks exactly like "no one has commented".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlockCommentCountsJsonTest {

    private static final int PROJECT_ID = 1;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectAccessSupport projectAccess;

    @MockBean
    private BlockCommentService blockCommentService;

    @Test
    void countsAreTopLevelAndKeyedByBlockId() throws Exception {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        when(blockCommentService.countsForProject(PROJECT_ID)).thenReturn(Map.of(12, 3L));

        mockMvc.perform(get("/api/block/comment-counts?projectId=" + PROJECT_ID)
                        .accept(MediaTypes.HAL_JSON).with(user("member").roles("USER")))
                .andExpect(status().isOk())
                // The map stays top-level under `counts`, keyed by block id.
                .andExpect(jsonPath("$.counts.['12']").value(3))
                .andExpect(jsonPath("$.counts.['99']").doesNotExist())
                .andExpect(jsonPath("$._links.self.href").exists())
                // Custom rels go out through the curie, as everywhere else.
                .andExpect(jsonPath("$._links.['scripty:blocks'].href").exists());
    }

    @Test
    void countsStayBehindAuthentication() throws Exception {
        mockMvc.perform(get("/api/block/comment-counts?projectId=" + PROJECT_ID)
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isUnauthorized());
    }
}
