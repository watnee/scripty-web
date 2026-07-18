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

    /** True when deleted blocks are kept until someone deletes them for good. */
    public boolean isRetentionUnlimited() {
        return retentionDays <= 0;
    }

    public List<DeletedBlockViewModel> getBlocks() {
        return blocks;
    }
}
