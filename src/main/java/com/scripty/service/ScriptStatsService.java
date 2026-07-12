package com.scripty.service;

import com.scripty.viewmodel.project.stats.ScriptStatsViewModel;

public interface ScriptStatsService {

    /**
     * Computes screenplay statistics for a project (or one of its script
     * editions). Returns null when the project does not exist.
     */
    ScriptStatsViewModel getStats(Integer projectId, Integer editionId, boolean canBrowseEditions);
}
