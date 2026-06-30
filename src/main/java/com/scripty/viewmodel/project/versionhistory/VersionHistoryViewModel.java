package com.scripty.viewmodel.project.versionhistory;

import java.util.List;

public class VersionHistoryViewModel {

    private int projectId;
    private String projectTitle;
    private List<VersionViewModel> versions;

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public List<VersionViewModel> getVersions() {
        return versions;
    }

    public void setVersions(List<VersionViewModel> versions) {
        this.versions = versions;
    }
}
