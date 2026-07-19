package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.DefaultCurieProvider;
import org.springframework.hateoas.server.core.AnnotationLinkRelationProvider;

/**
 * The {@code _embedded} keys are a client contract — fountain-power.js reads
 * characters out of one by name — so every resource type declares an explicit
 * {@code @Relation} and this test pins the result.
 *
 * <p>Without the annotation the key is derived from the class name
 * ({@code personResourceList}), which makes an ordinary rename a silent
 * breaking change for clients. {@link ContactSuggestionRelationTest} covers the
 * same contract for the one view model that also embeds.
 */
class ResourceRelationTest {

    private static final AnnotationLinkRelationProvider RELATIONS = new AnnotationLinkRelationProvider();

    /** Mirrors the provider {@code HypermediaConfig} registers. */
    private static final DefaultCurieProvider CURIES =
            new DefaultCurieProvider("scripty", UriTemplate.of("/docs/api-rels.html#{rel}"));

    static Stream<Arguments> resources() {
        return Stream.of(
                Arguments.of(ProjectResource.class, "project", "projects"),
                Arguments.of(ActorResource.class, "actor", "actors"),
                Arguments.of(PersonResource.class, "character", "characters"),
                Arguments.of(TeamResource.class, "team", "teams"),
                Arguments.of(UserResource.class, "user", "users"),
                Arguments.of(BlockResource.class, "block", "blocks"),
                Arguments.of(SongBlockResource.class, "songBlock", "songBlocks"),
                Arguments.of(SongVersionResource.class, "version", "songVersions"),
                Arguments.of(ProjectVersionResource.class, "version", "versions"),
                Arguments.of(TextDocumentResource.class, "document", "documents"),
                Arguments.of(ScriptEditionResource.class, "edition", "editions"),
                Arguments.of(SongEditionResource.class, "songEdition", "songEditions"),
                Arguments.of(BlockCommentResource.class, "comment", "comments"),
                Arguments.of(ProjectActivityResource.class, "activityEntry", "activity"),
                Arguments.of(InvitationResource.class, "invitation", "invitations"),
                Arguments.of(DeletedBlockResource.class, "deletedBlock", "deletedBlocks"),
                Arguments.of(TrashedProjectResource.class, "trashedProject", "trashedProjects"),
                Arguments.of(DeletedDocumentResource.class, "deletedDocument", "deletedDocuments"));
    }

    @ParameterizedTest(name = "{0} embeds as {2}")
    @MethodSource("resources")
    void resourcesEmbedUnderTheirDeclaredRelations(Class<?> type, String item, String collection) {
        assertEquals(item, RELATIONS.getItemResourceRelFor(type).value());
        assertEquals(collection, RELATIONS.getCollectionResourceRelFor(type).value());
    }

    @ParameterizedTest(name = "{2} renders as scripty:{2}")
    @MethodSource("resources")
    void renderedEmbedKeysCarryTheCuriePrefix(Class<?> type, String item, String collection) {
        assertEquals("scripty:" + collection,
                CURIES.getNamespacedRelFor(LinkRelation.of(collection)).value());
    }
}
