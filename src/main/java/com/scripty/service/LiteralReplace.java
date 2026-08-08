package com.scripty.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The find-and-replace matching rule, in one place.
 *
 * <p>Both {@link BlockServiceImpl} (a screenplay's elements) and
 * {@link SongBlockServiceImpl} (a song's lyric lines) rewrite text on the same
 * terms, and the clients mirror these terms to decide which rows to send and to
 * show a writer how many lines a Replace All will touch. The Apple client's
 * {@code ScriptSearchModel.containsMatch} is a direct port of {@link #pattern}.
 * A second copy of the rule here would let the song side drift from the
 * screenplay side, and the only visible symptom would be a tally that quietly
 * disagreed with what the server actually rewrote — so the rule is compiled
 * once and shared rather than written twice.
 *
 * <p>The term is always literal. Regex search is deliberately not exposed to
 * users: {@code $1}, {@code .*} and a stray backslash all mean themselves, on
 * the find side via {@link Pattern#quote} and on the replace side via
 * {@link Matcher#quoteReplacement}.
 */
final class LiteralReplace {

    private LiteralReplace() {
    }

    /**
     * Compiles {@code find} as a literal term.
     *
     * <p>{@code wholeWord} anchors it between word boundaries, so {@code art}
     * stops matching inside {@code start}. Without {@code matchCase} the match
     * is case-insensitive for non-ASCII letters too — {@code UNICODE_CASE} is
     * what makes {@code é} fold the way a writer expects.
     */
    static Pattern pattern(String find, boolean matchCase, boolean wholeWord) {
        String quoted = Pattern.quote(find);
        if (wholeWord) {
            quoted = "\\b" + quoted + "\\b";
        }
        int flags = matchCase ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return Pattern.compile(quoted, flags);
    }

    /** {@code replace} as a literal replacement string, never a substitution template. */
    static String replacement(String replace) {
        return Matcher.quoteReplacement(replace != null ? replace : "");
    }
}
