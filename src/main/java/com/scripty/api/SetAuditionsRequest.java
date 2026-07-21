package com.scripty.api;

import java.util.List;

/**
 * The characters an actor auditions for in a project. The whole set is replaced
 * on every write, so an empty list clears the actor's auditions; a null list is
 * treated the same, since "audition for nothing" is the only sensible reading of
 * a set with no members.
 */
public record SetAuditionsRequest(List<Integer> characterIds) {
}
