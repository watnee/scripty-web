package com.scripty.api;

/**
 * Body for {@code POST /api/song/block/{id}/below}. The anchor line comes from
 * the path; the new line below it always starts empty.
 *
 * <p>{@code afterContent} saves the anchor's text in the same call, matching
 * the web editor: pressing Enter mid-line commits what was typed and opens a
 * fresh line beneath. Null leaves the anchor's stored content alone.
 */
public record CreateSongBlockBelowRequest(String afterContent) {
}
