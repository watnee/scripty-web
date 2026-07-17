package com.scripty.api;

/**
 * Body for {@code PUT /api/song/block/{id}}. A null {@code content} is stored as
 * an empty line rather than rejected — clearing a lyric line is a normal edit.
 */
public record EditSongBlockRequest(String content) {
}
