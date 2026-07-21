package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every project rel is namespaced through the HAL curie {@code HypermediaConfig}
 * registers, whose template is {@code /docs/api-rels.html#{rel}}. That makes the
 * documentation page part of the wire contract: a rel with no matching anchor
 * hands clients a link that resolves to nothing.
 *
 * <p>The gap this guards against is silent — adding a constant to {@link ApiRel}
 * and advertising it breaks no test and shows no error, it just publishes a
 * dead link. It had accumulated across 29 relations before anyone noticed, so
 * the check is cheap next to rediscovering it.
 */
class ApiRelDocumentationTest {

    private static final Path DOC =
            Path.of("src/main/resources/static/docs/api-rels.html");

    /** {@code <tr id="…">} is how each row is anchored, and what the curie targets. */
    private static final Pattern ANCHOR = Pattern.compile("<tr id=\"([^\"]+)\"");

    private static List<String> declaredRels() {
        List<String> rels = new ArrayList<>();
        for (Field field : ApiRel.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && Modifier.isPublic(field.getModifiers())
                    && field.getType() == String.class) {
                try {
                    rels.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError("unreadable ApiRel field " + field.getName(), e);
                }
            }
        }
        return rels;
    }

    private static Set<String> documentedAnchors() throws IOException {
        String html = Files.readString(DOC, StandardCharsets.UTF_8);
        Set<String> anchors = new LinkedHashSet<>();
        Matcher m = ANCHOR.matcher(html);
        while (m.find()) {
            anchors.add(m.group(1));
        }
        return anchors;
    }

    @Test
    void everyRelHasADocumentationAnchor() throws IOException {
        Set<String> documented = documentedAnchors();
        List<String> missing = declaredRels().stream()
                .filter(rel -> !documented.contains(rel))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            fail("These rels are advertised but have no <tr id=\"…\"> row in "
                    + DOC + ", so their curie link resolves to nothing: " + missing
                    + ". Add a row to the Navigation table (what it leads to) or the"
                    + " Mutations table (method and action).");
        }
    }

    /**
     * The reverse direction: a row describing a rel the server never emits
     * promises a client something it will not find. {@code preferences} was
     * documented for exactly this long after its constant went unused.
     */
    @Test
    void everyDocumentedAnchorIsARealRel() throws IOException {
        List<String> rels = declaredRels();
        // Anchors for rels that are IANA-standard or declared outside ApiRel.
        Set<String> exempt = new LinkedHashSet<>(Arrays.asList("self"));
        List<String> stale = documentedAnchors().stream()
                .filter(anchor -> !rels.contains(anchor))
                .filter(anchor -> !exempt.contains(anchor))
                .sorted()
                .toList();
        if (!stale.isEmpty()) {
            fail("These rows in " + DOC + " document a rel no ApiRel constant declares,"
                    + " so they advertise something the server never sends: " + stale);
        }
    }

    @Test
    void theDocumentIsWhereTheCurieSaysItIs() {
        assertTrue(Files.exists(DOC),
                "the curie template in HypermediaConfig points clients at " + DOC);
    }
}
