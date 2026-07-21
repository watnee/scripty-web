package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.api.ApiRel;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.BlockCommentService;
import java.security.Principal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The one call that says which elements have discussion on them. The shape
 * matters as much as the authorization: a client paints a badge per element, so
 * the map is keyed by block id and omits the elements with nothing on them.
 */
class BlockCommentCountsRestTest {

    private static final int PROJECT_ID = 3;

    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final BlockCommentService blockCommentService = mock(BlockCommentService.class);
    private final BlockCommentRestController controller = new BlockCommentRestController();

    @BeforeEach
    void setUp() {
        controller.projectAccess = projectAccess;
        controller.blockCommentService = blockCommentService;
        // linkTo(methodOn(...)) needs a current request to build absolute hrefs.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> countsOf(ResponseEntity<?> response) {
        EntityModel<Map<String, Object>> body = (EntityModel<Map<String, Object>>) response.getBody();
        return (Map<String, Long>) body.getContent().get("counts");
    }

    @Test
    void countsAreKeyedByBlockId() {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        when(blockCommentService.countsForProject(PROJECT_ID)).thenReturn(Map.of(5, 2L, 9, 1L));

        ResponseEntity<?> response = controller.commentCounts(PROJECT_ID, mock(Principal.class));
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Long> counts = countsOf(response);
        // JSON object keys are strings, so the ids go out stringified rather
        // than relying on the serializer to coerce them.
        assertEquals(2L, counts.get("5"));
        assertEquals(1L, counts.get("9"));
        assertFalse(counts.containsKey("7"), "an element with no comments is absent, not zero");
    }

    @Test
    void anUncommentedScriptAnswersWithAnEmptyMap() {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        when(blockCommentService.countsForProject(PROJECT_ID)).thenReturn(Map.of());

        ResponseEntity<?> response = controller.commentCounts(PROJECT_ID, mock(Principal.class));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(countsOf(response).isEmpty());
    }

    @Test
    void theCountsPointBackAtTheScript() {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        when(blockCommentService.countsForProject(PROJECT_ID)).thenReturn(Map.of(5, 2L));

        ResponseEntity<?> response = controller.commentCounts(PROJECT_ID, mock(Principal.class));
        assertTrue(((EntityModel<?>) response.getBody()).getLink(ApiRel.BLOCKS).isPresent());
    }

    /** Reading comments needs read access — but it does need that much. */
    @Test
    void outsiderIsRefused() {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(false);
        assertEquals(HttpStatus.FORBIDDEN,
                controller.commentCounts(PROJECT_ID, mock(Principal.class)).getStatusCode());
    }
}
