package com.scripty.api;

/**
 * Request bodies for the script-edition endpoints.
 *
 * <p>Creating an edition may copy the script from an existing one — which is
 * how a revision starts life as a duplicate of the draft it revises. Omitting
 * {@code copyFromEditionId} makes an empty edition instead.
 */
public final class ScriptEditionRequests {

    private ScriptEditionRequests() {
    }

    public record Create(String name, Integer copyFromEditionId) {
    }

    public record Rename(String name) {
    }
}
