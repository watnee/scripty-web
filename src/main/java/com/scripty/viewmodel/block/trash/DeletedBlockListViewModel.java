package com.scripty.viewmodel.block.trash;

import java.util.List;

/** The block trash page: a project's deleted blocks, newest first. */
public class DeletedBlockListViewModel {

    private final int projectId;
    private final String projectTitle;
    private final int retentionDays;
    private final List<DeletedBlockViewModel> blocks;

    public DeletedBlockListViewModel(int projectId, String projectTitle, int retentionDays,
                                     List<DeletedBlockViewModel> blocks) {
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.retentionDays = retentionDays;
        this.blocks = blocks;
    }

    public int getProjectId() {
        return projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public List<DeletedBlockViewModel> getBlocks() {
        return blocks;
    }
}
