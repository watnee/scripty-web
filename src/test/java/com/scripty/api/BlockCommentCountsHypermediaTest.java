package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.viewmodel.block.BlockViewModel;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Comment counts are how a client marks the elements that have discussion on
 * them without asking about every line. The rel is what a hypermedia client
 * gates on, so this pins that the block collection advertises it — and, just as
 * importantly, that it is not swept up in the edit gate: a reader is exactly
 * who wants to see where the conversation is.
 *
 * <p>The endpoint behind the rel is covered by
 * {@code BlockCommentCountsRestTest}.
 */
class BlockCommentCountsHypermediaTest {

    private static final int PROJECT_ID = 3;

    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final BlockResourceAssembler assembler = new BlockResourceAssembler();

    @BeforeEach
    void setUp() {
        assembler.projectAccess = projectAccess;

        // linkTo(methodOn(...)) needs a current request to build absolute hrefs.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        Authentication authentication =
                new UsernamePasswordAuthenticationToken("member", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(projectAccess.currentUser(any(Authentication.class))).thenReturn(new User());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private static boolean hasLink(RepresentationModel<?> model, String rel) {
        return model.getLink(rel).isPresent();
    }

    private List<BlockViewModel> oneBlock() {
        BlockViewModel block = new BlockViewModel();
        block.setId(5);
        return List.of(block);
    }

    @Test
    void writerSeesCommentCounts() {
        when(projectAccess.canEditScriptForCurrentUser(PROJECT_ID)).thenReturn(true);
        CollectionModel<EntityModel<BlockResource>> collection =
                assembler.toBlockCollection(oneBlock(), PROJECT_ID);
        assertTrue(hasLink(collection, ApiRel.COMMENT_COUNTS),
                "a script with elements in it should say where their comment counts live");
    }

    @Test
    void readerStillSeesCommentCounts() {
        when(projectAccess.canEditScriptForCurrentUser(PROJECT_ID)).thenReturn(false);
        CollectionModel<EntityModel<BlockResource>> collection =
                assembler.toBlockCollection(oneBlock(), PROJECT_ID);
        assertTrue(hasLink(collection, ApiRel.COMMENT_COUNTS));
        assertFalse(hasLink(collection, ApiRel.BULK_DELETE),
                "while the edit affordances stay gated");
    }

    /** An empty script has nothing to count against, so the rel stays off. */
    @Test
    void emptyScriptDoesNotAdvertiseCommentCounts() {
        when(projectAccess.canEditScriptForCurrentUser(PROJECT_ID)).thenReturn(true);
        CollectionModel<EntityModel<BlockResource>> collection =
                assembler.toBlockCollection(List.of(), PROJECT_ID);
        assertFalse(hasLink(collection, ApiRel.COMMENT_COUNTS));
    }
}
