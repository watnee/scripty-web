package com.scripty.api;

/**
 * Body for {@code POST /api/block/{id}/move}. {@code position} is the absolute
 * order the block should end up at, matching the {@code order} field the block
 * collection reports.
 */
public record MoveBlockRequest(Integer position) {
}
