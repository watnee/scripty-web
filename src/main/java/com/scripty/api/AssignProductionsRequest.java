package com.scripty.api;

import java.util.List;

/**
 * The productions a team works on. An empty list assigns none; a null list is
 * rejected rather than treated as "leave alone", so clearing is expressible.
 */
public record AssignProductionsRequest(List<Integer> projectIds) {
}
