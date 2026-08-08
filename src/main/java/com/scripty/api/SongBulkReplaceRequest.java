package com.scripty.api;

import java.util.List;

/**
 * Body for {@code POST /api/song/block/bulk/replace}: replaces every occurrence
 * of {@code find} with {@code replace} across a song's lyric lines — the song
 * counterpart of {@link BulkReplaceRequest}'s "Replace All".
 *
 * <p>Carries only the operation. Which song and which version are query
 * parameters, as they are on {@code list}, {@code append}, {@code undo} and
 * {@code redo} — the shape every route on this controller already uses, and the
 * one that lets the assembler hand a client a link with the version already in
 * it. That matters here: the iOS client deliberately tracks the version it is
 * showing as a link rather than as an id it assembles, so a body field it would
 * have to fill in is a field it cannot fill in correctly.
 *
 * <p>Deliberately not a {@link BulkBlockRequest}. That interface carries a
 * {@code projectId} and is authorised project-wide, whereas a song is reached
 * and authorised through its <em>document</em>.
 *
 * <p>There is no {@code includeCharacterCues} either: a lyric line has no
 * character cue to desync from a person record, so every line is fair game.
 *
 * <p>An absent {@code ids} means every line in the version, which is what
 * "Replace All in this song" means to a writer. A supplied list narrows it, and
 * is intersected server-side with the version's own lines — so a caller cannot
 * smuggle another song's ids past the document-level permission check.
 *
 * <p>{@code find} is matched literally, never as a regular expression, and
 * {@code replace} is inserted literally. The boxed booleans default to false
 * when omitted.
 */
public record SongBulkReplaceRequest(
        List<Integer> ids,
        String find,
        String replace,
        Boolean matchCase,
        Boolean wholeWord) {

    public String replacementOrEmpty() {
        return replace != null ? replace : "";
    }

    public boolean matchCaseOrFalse() {
        return Boolean.TRUE.equals(matchCase);
    }

    public boolean wholeWordOrFalse() {
        return Boolean.TRUE.equals(wholeWord);
    }
}
