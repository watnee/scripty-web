package com.scripty.api;

/**
 * Body for {@code POST /api/song/block/{id}/move}. {@code position} is the
 * zero-based index the line should end up at, matching the {@code order} field
 * the song block collection reports. Out-of-range values clamp to the song's
 * bounds.
 */
public record MoveSongBlockRequest(Integer position) {
}
