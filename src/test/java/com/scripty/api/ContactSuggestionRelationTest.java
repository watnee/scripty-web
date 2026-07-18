package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scripty.viewmodel.contact.ContactSuggestionViewModel;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.server.core.AnnotationLinkRelationProvider;

/**
 * contact-autofill.js reads the suggestions out of {@code _embedded} by name,
 * so the embed key is a client contract, not an implementation detail. Without
 * the {@code @Relation} annotation it would be derived from the class name and
 * would change under an ordinary rename.
 */
class ContactSuggestionRelationTest {

    @Test
    void suggestionsEmbedUnderTheirDeclaredCollectionRelation() {
        assertEquals("contactSuggestions",
                new AnnotationLinkRelationProvider()
                        .getCollectionResourceRelFor(ContactSuggestionViewModel.class)
                        .value());
    }
}
