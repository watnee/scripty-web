package com.scripty.api;

import java.util.List;

/**
 * Replaces every occurrence of {@code find} with {@code replace} across the
 * named blocks.
 *
 * <p>{@code find} is matched literally, never as a regular expression, and
 * {@code replace} is inserted literally — {@code $1} and backslashes carry no
 * special meaning. Character cue blocks are skipped unless
 * {@code includeCharacterCues} is set, because a cue's content mirrors its
 * person record and rewriting one would desync the two.
 *
 * <p>The boxed booleans default to false when omitted.
 */
public record BulkReplaceRequest(
        List<Integer> ids,
        Integer projectId,
        String find,
        String replace,
        Boolean matchCase,
        Boolean wholeWord,
        Boolean includeCharacterCues) implements BulkBlockRequest {

    public String replacementOrEmpty() {
        return replace != null ? replace : "";
    }

    public boolean matchCaseOrFalse() {
        return Boolean.TRUE.equals(matchCase);
    }

    public boolean wholeWordOrFalse() {
        return Boolean.TRUE.equals(wholeWord);
    }

    public boolean includeCharacterCuesOrFalse() {
        return Boolean.TRUE.equals(includeCharacterCues);
    }
}
