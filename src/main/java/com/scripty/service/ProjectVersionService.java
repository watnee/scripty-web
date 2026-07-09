package com.scripty.service;

import com.scripty.dto.ProjectVersion;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;

public interface ProjectVersionService {

    VersionHistoryViewModel getVersionHistoryViewModel(Integer projectId);

    ProjectVersion createVersion(Integer projectId, String label);

    void autoSaveVersion(Integer projectId);

    void autoSaveVersionForBlock(Integer blockId);

    void autoSaveVersionForPerson(Integer personId);

    void restoreVersion(Integer versionId);

    /**
     * Restores a version only when it belongs to {@code projectId}.
     * @return true if restored, false if the version is missing or belongs to another project
     */
    boolean restoreVersionForProject(Integer versionId, Integer projectId);

    void deleteVersion(Integer versionId);

    /**
     * Deletes a version only when it belongs to {@code projectId}.
     * @return true if deleted, false if the version is missing or belongs to another project
     */
    boolean deleteVersionForProject(Integer versionId, Integer projectId);

    String buildSnapshotJson(Integer projectId);

    void applySnapshotJson(Integer projectId, String snapshotJson);
}
