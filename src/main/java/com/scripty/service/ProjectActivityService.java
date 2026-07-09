package com.scripty.service;

import com.scripty.dto.User;
import com.scripty.viewmodel.activity.ProjectActivityViewModel;
import java.util.List;

public interface ProjectActivityService {

    void record(Integer projectId, Integer actorUserId, String actionType, String summary,
                String entityType, Integer entityId);

    /**
     * Records activity for the current authenticated user when available.
     * Used by services that do not receive a User parameter.
     */
    void recordForCurrentUser(Integer projectId, String actionType, String summary,
                              String entityType, Integer entityId);

    List<ProjectActivityViewModel> listRecent(Integer projectId, User currentUser, int limit);
}
