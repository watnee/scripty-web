package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * One person who can already see a project.
 *
 * <p>This is not the invitation list. Access follows from role and team
 * membership as much as from an invitation, so a project can be readable by
 * people nobody ever invited — which is exactly what a writer is asking about
 * when they wonder who else is looking at the draft.
 *
 * <p>Labels are sent rendered rather than as ids, because the reasons ("On the
 * Lighting team", "Producer") come from the same rules the web page states in
 * prose, and re-deriving them client-side would let the two drift apart.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.ACCESS_USER, collectionRelation = ApiRel.ACCESS)
public class ProjectAccessUserResource extends RepresentationModel<ProjectAccessUserResource> {

    private String displayName;
    private String accessLabel;
    private boolean canEdit;
    private String permissionLabel;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** Why this person can see it — their role, or the team that carries the project. */
    public String getAccessLabel() {
        return accessLabel;
    }

    public void setAccessLabel(String accessLabel) {
        this.accessLabel = accessLabel;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    public void setCanEdit(boolean canEdit) {
        this.canEdit = canEdit;
    }

    /** The rendered form of {@link #isCanEdit()} — "Can edit" or "View only". */
    public String getPermissionLabel() {
        return permissionLabel;
    }

    public void setPermissionLabel(String permissionLabel) {
        this.permissionLabel = permissionLabel;
    }
}
