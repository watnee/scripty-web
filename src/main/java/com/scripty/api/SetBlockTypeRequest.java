package com.scripty.api;

/**
 * Body for {@code POST /api/block/{id}/type} — the REST counterpart of the web
 * editor's element-type bar.
 *
 * <p>Only {@code type} is required. A null {@code content} or {@code tags}
 * leaves the stored value untouched, so a client can retype an element without
 * having to resend the text it already has.
 */
public record SetBlockTypeRequest(String type, String content, Integer personId, String tags) {
}
