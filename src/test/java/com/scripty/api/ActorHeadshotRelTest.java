package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.viewmodel.actor.actorlist.ActorViewModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.EntityModel;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The headshot write rels. Storing one is always on offer; reading and removing
 * one are offered only where there is a headshot, which is what lets a client
 * decide what to draw from the links alone.
 *
 * <p>Worth pinning because the failure is invisible from the server's side: get
 * the condition backwards and a client either hides an upload button that would
 * have worked, or offers to delete a headshot that was never there. Neither
 * throws anything.
 */
class ActorHeadshotRelTest {

    private final ActorResourceAssembler actors = new ActorResourceAssembler();

    @BeforeEach
    void setUp() {
        // linkTo(methodOn(...)) needs a current request to build absolute hrefs.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static ActorViewModel actor(boolean hasHeadshot) {
        ActorViewModel actor = new ActorViewModel();
        actor.setId(4);
        actor.setFirst("Nadia");
        actor.setLast("Okonjo");
        actor.setHasHeadshot(hasHeadshot);
        return actor;
    }

    @Test
    void offersUploadWhetherOrNotThereIsOneAlready() {
        assertTrue(actors.toModel(actor(false)).hasLink(ApiRel.SET_HEADSHOT),
                "an actor with no headshot must still be offered one");
        assertTrue(actors.toModel(actor(true)).hasLink(ApiRel.SET_HEADSHOT),
                "replacing a headshot is the same action as adding one");
    }

    @Test
    void offersRemovalAndTheImageOnlyWhenThereIsOne() {
        EntityModel<ActorResource> without = actors.toModel(actor(false));
        assertFalse(without.hasLink(ApiRel.REMOVE_HEADSHOT),
                "nothing to remove");
        assertFalse(without.hasLink(ApiRel.HEADSHOT),
                "nothing to fetch");

        EntityModel<ActorResource> with = actors.toModel(actor(true));
        assertTrue(with.hasLink(ApiRel.REMOVE_HEADSHOT));
        assertTrue(with.hasLink(ApiRel.HEADSHOT));
    }

    /**
     * A project-scoped actor is the one the casting screens actually list, so
     * the rels have to survive that path too — it builds its links through a
     * different overload.
     */
    @Test
    void survivesTheProjectScopedPath() {
        EntityModel<ActorResource> scoped = actors.toModel(actor(true), 11);
        assertTrue(scoped.hasLink(ApiRel.SET_HEADSHOT));
        assertTrue(scoped.hasLink(ApiRel.REMOVE_HEADSHOT));
    }
}
