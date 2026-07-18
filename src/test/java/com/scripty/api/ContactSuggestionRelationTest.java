package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scripty.viewmodel.contact.ContactSuggestionViewModel;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.DefaultCurieProvider;
import org.springframework.hateoas.server.core.AnnotationLinkRelationProvider;

/**
 * contact-autofill.js reads the suggestions out of {@code _embedded} by name,
 * so the embed key is a client contract, not an implementation detail.
 *
 * <p>Two things decide that key, and the script needs both: the
 * {@code @Relation} annotation (without it the name is derived from the class
 * and would change under an ordinary rename) and the curie provider, which
 * namespaces embedded rels exactly as it namespaces links. Asserting only the
 * first is what let a mismatch ship — the relation resolved to
 * {@code contactSuggestions} while the wire format said
 * {@code scripty:contactSuggestions}, and the dropdown silently found nothing.
 */
class ContactSuggestionRelationTest {

    @Test
    void suggestionsEmbedUnderTheirDeclaredCollectionRelation() {
        assertEquals("contactSuggestions",
                new AnnotationLinkRelationProvider()
                        .getCollectionResourceRelFor(ContactSuggestionViewModel.class)
                        .value());
    }

    @Test
    void renderedEmbedKeyCarriesTheCuriePrefixTheScriptReads() {
        // Mirrors the provider HypermediaConfig registers.
        DefaultCurieProvider curies =
                new DefaultCurieProvider("scripty", UriTemplate.of("/docs/api-rels.html#{rel}"));

        assertEquals("scripty:contactSuggestions",
                curies.getNamespacedRelFor(LinkRelation.of("contactSuggestions")).value());
    }
}
