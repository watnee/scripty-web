package com.scripty.api;

/**
 * Body for {@code POST /api/song/block/{id}/highlight}. The colour is one of
 * {@link com.scripty.dto.Block#HIGHLIGHTS}; a blank or unknown value clears the
 * tint.
 */
public record SetSongBlockHighlightRequest(String highlight) {
}
