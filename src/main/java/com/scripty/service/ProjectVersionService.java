package com.scripty.service;

import com.scripty.dto.ProjectVersion;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;

public interface ProjectVersionService {

    VersionHistoryViewModel getVersionHistoryViewModel(Integer projectId);

    ProjectVersion createVersion(Integer projectId, String label);

    void autoSaveVersion(Integer projectId);

    void autoSaveVersionForScene(Integer sceneId);

    void autoSaveVersionForBlock(Integer blockId);

    void autoSaveVersionForPerson(Integer personId);

    void restoreVersion(Integer versionId);

    void deleteVersion(Integer versionId);

    String buildSnapshotJson(Integer projectId);

    void applySnapshotJson(Integer projectId, String snapshotJson);
}
