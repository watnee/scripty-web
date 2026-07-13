package com.scripty.viewmodel.project.projectprofile;

public class ProjectShareUserViewModel {

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

    public String getPermissionLabel() {
        return permissionLabel;
    }

    public void setPermissionLabel(String permissionLabel) {
        this.permissionLabel = permissionLabel;
    }
}
