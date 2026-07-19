package com.scripty.api;

/**
 * An invitation to send.
 *
 * <p>{@code viewOnly} chooses between a collaborator, who gets an account and
 * can edit, and a reader, who gets a tokenized link and cannot. {@code teamId}
 * applies only to collaborators.
 */
public record SendInvitationRequest(String email, Integer teamId, Boolean viewOnly) {

    public boolean viewOnlyOrFalse() {
        return Boolean.TRUE.equals(viewOnly);
    }
}
