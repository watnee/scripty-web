package com.scripty.api;

/**
 * Body for {@code POST /api/block/{id}/below}. The anchor block comes from the
 * path; this carries what the new block below it should hold.
 *
 * <p>Content may be empty: an inline editor inserts a blank block for the
 * writer to type into.
 */
public record CreateBlockBelowRequest(String content, Integer personId, String type) {
}
