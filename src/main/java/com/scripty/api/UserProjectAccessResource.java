package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One project a user can reach, and why. Carried on the single-user resource
 * (the {@code GET /api/user/{id}} profile) so an admin can see a person's
 * access at a glance — which projects, the reason (a privileged role, a team,
 * or an open project) and whether it is view-only or editable. It is a plain
 * nested value, not a linkable resource of its own: the project link lives on
 * the project collection, and this is a read-only diagnostic breakdown.
 *
 * <p>Mirrors the web profile page's per-project access rows
 * ({@code user/show.html}), which read the same computed
 * {@code UserProjectAccessViewModel}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProjectAccessResource {

    private Integer projectId;
    private String projectName;
    private Boolean canEdit;
    private String permissionLabel;
    private String accessReason;

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Boolean getCanEdit() {
        return canEdit;
    }

    public void setCanEdit(Boolean canEdit) {
        this.canEdit = canEdit;
    }

    public String getPermissionLabel() {
        return permissionLabel;
    }

    public void setPermissionLabel(String permissionLabel) {
        this.permissionLabel = permissionLabel;
    }

    public String getAccessReason() {
        return accessReason;
    }

    public void setAccessReason(String accessReason) {
        this.accessReason = accessReason;
    }
}
