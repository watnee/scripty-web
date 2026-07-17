package com.scripty.api;

/**
 * Body for {@code POST /api/song/block/{id}/below}. {@code content} is the text
 * of the new line, matching {@link CreateBlockBelowRequest} — note this differs
 * from the HTMX editor's createBelow, where the posted content belongs to the
 * line being split. May be blank: clients typically insert an empty line and
 * fill it in as the writer types.
 */
public record CreateSongBlockBelowRequest(String content) {
}
