package com.scripty.viewmodel.project.projecttrash;

import java.util.ArrayList;
import java.util.List;

public class ProjectTrashViewModel {

    private List<ProjectTrashItemViewModel> projects = new ArrayList<>();
    private int retentionDays;

    public List<ProjectTrashItemViewModel> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectTrashItemViewModel> projects) {
        this.projects = projects;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}
