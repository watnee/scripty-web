package com.scripty.api;

/**
 * Body for {@code PUT /api/song/block/{id}}. Content may be empty — a lyric
 * line the writer has cleared but not deleted.
 */
public record EditSongBlockRequest(String content) {
}
