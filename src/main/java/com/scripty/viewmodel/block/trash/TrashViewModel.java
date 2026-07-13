package com.scripty.viewmodel.block.trash;

import java.util.ArrayList;
import java.util.List;

public class TrashViewModel {

    private Integer projectId;
    private String projectTitle;
    private int retentionDays;
    private List<TrashBlockViewModel> blocks = new ArrayList<>();

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public List<TrashBlockViewModel> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<TrashBlockViewModel> blocks) {
        this.blocks = blocks;
    }
}
