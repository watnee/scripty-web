package com.scripty.api;

/**
 * Body for {@code POST /api/block/{id}/replace}: replaces a single occurrence of
 * {@code find} with {@code replace} inside one block — the one-at-a-time
 * counterpart of {@link BulkReplaceRequest}'s "Replace All".
 *
 * <p>{@code find} is matched literally, never as a regular expression, and
 * {@code replace} is inserted literally. {@code occurrence} is the zero-based
 * index of the match to swap within the block, matching the order the block's
 * own text reads; an out-of-range index changes nothing. The boxed booleans
 * default to false when omitted.
 */
public record ReplaceOccurrenceRequest(
        String find,
        String replace,
        Boolean matchCase,
        Boolean wholeWord,
        Integer occurrence) {

    public String replacementOrEmpty() {
        return replace != null ? replace : "";
    }

    public boolean matchCaseOrFalse() {
        return Boolean.TRUE.equals(matchCase);
    }

    public boolean wholeWordOrFalse() {
        return Boolean.TRUE.equals(wholeWord);
    }

    public int occurrenceOrFirst() {
        return occurrence != null ? occurrence : 0;
    }
}
