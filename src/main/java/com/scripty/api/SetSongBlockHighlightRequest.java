package com.scripty.api;

/**
 * Body for {@code POST /api/song/block/{id}/highlight}. An unknown or blank
 * {@code highlight} clears the tint; see {@link com.scripty.dto.Block#HIGHLIGHTS}
 * for the accepted colors.
 */
public record SetSongBlockHighlightRequest(String highlight) {
}
