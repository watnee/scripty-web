package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * Someone who has been invited to a screenplay — a collaborator who will get an
 * account, or a view-only reader who will not.
 *
 * <p>Carries no token and no invite URL, matching the view models it is built
 * from. A view token is a long-lived credential for reading a whole screenplay;
 * saying who was invited is a much smaller promise than handing out the means
 * to read it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.INVITATION, collectionRelation = ApiRel.INVITATIONS)
public class InvitationResource extends RepresentationModel<InvitationResource> {

    private Integer id;
    private String email;
    private String teamName;
    private String statusLabel;
    private boolean viewOnly;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public boolean isViewOnly() {
        return viewOnly;
    }

    public void setViewOnly(boolean viewOnly) {
        this.viewOnly = viewOnly;
    }
}
