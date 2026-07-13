package com.scripty.service;

import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.User;
import com.scripty.repository.ProjectActivityRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.UserRepository;
import com.scripty.viewmodel.activity.ProjectActivityViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectActivityServiceImpl implements ProjectActivityService {

    private static final int SCRIPT_EDIT_ROLLUP_MINUTES = 15;
    private static final String DEFAULT_ACTOR_NAME = "Someone";

    private final ProjectActivityRepository projectActivityRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ProjectService projectService;

    @Autowired
    public ProjectActivityServiceImpl(ProjectActivityRepository projectActivityRepository,
                                      ProjectRepository projectRepository,
                                      UserRepository userRepository,
                                      UserService userService,
                                      @Lazy ProjectService projectService) {
        this.projectActivityRepository = projectActivityRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.projectService = projectService;
    }

    @Override
    @Transactional
    public void record(Integer projectId, Integer actorUserId, String actionType, String summary,
                       String entityType, Integer entityId) {
        if (projectId == null || actionType == null || actionType.isBlank()
                || summary == null || summary.isBlank()) {
            return;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        User actor = actorUserId != null ? userRepository.findById(actorUserId).orElse(null) : null;

        if (ProjectActivity.ACTION_SCRIPT_EDITED.equals(actionType) && actor != null) {
            List<ProjectActivity> existing = projectActivityRepository.findLatestRollupCandidates(
                    projectId,
                    ProjectActivity.ACTION_SCRIPT_EDITED,
                    actor.getId(),
                    now.minusMinutes(SCRIPT_EDIT_ROLLUP_MINUTES),
                    PageRequest.of(0, 1));
            if (!existing.isEmpty()) {
                ProjectActivity rollup = existing.get(0);
                rollup.setCreatedAt(now);
                rollup.setSummary(summary);
                projectActivityRepository.save(rollup);
                return;
            }
        }

        ProjectActivity activity = new ProjectActivity();
        activity.setProject(project);
        activity.setActorUser(actor);
        activity.setActionType(actionType);
        activity.setSummary(truncate(summary, 500));
        activity.setEntityType(entityType);
        activity.setEntityId(entityId);
        activity.setCreatedAt(now);
        projectActivityRepository.save(activity);
    }

    @Override
    @Transactional
    public void recordForCurrentUser(Integer projectId, String actionType, String summary,
                                     String entityType, Integer entityId) {
        Integer actorUserId = resolveCurrentUserId();
        record(projectId, actorUserId, actionType, summary, entityType, entityId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectActivityViewModel> listRecent(Integer projectId, User currentUser, int limit) {
        if (projectId == null || currentUser == null || limit <= 0) {
            return Collections.emptyList();
        }
        if (!projectService.canUserAccessProject(projectId, currentUser)) {
            return Collections.emptyList();
        }
        List<ProjectActivity> activities = projectActivityRepository.findRecentByProjectId(
                projectId, PageRequest.of(0, limit));
        List<ProjectActivityViewModel> result = new ArrayList<>(activities.size());
        for (ProjectActivity activity : activities) {
            result.add(toViewModel(activity));
        }
        return result;
    }

    private ProjectActivityViewModel toViewModel(ProjectActivity activity) {
        ProjectActivityViewModel vm = new ProjectActivityViewModel();
        vm.setId(activity.getId());
        vm.setActionType(activity.getActionType());
        vm.setSummary(activity.getSummary());
        vm.setCreatedAt(activity.getCreatedAt());
        User actor = activity.getActorUser();
        if (actor == null) {
            vm.setActorDisplayName(DEFAULT_ACTOR_NAME);
        } else {
            String first = actor.getFirstName() != null ? actor.getFirstName().trim() : "";
            String last = actor.getLastName() != null ? actor.getLastName().trim() : "";
            String full = (first + " " + last).trim();
            if (full.isEmpty()) {
                full = actor.getUsername() != null ? actor.getUsername() : DEFAULT_ACTOR_NAME;
            }
            vm.setActorDisplayName(full);
        }
        return vm;
    }

    private Integer resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return null;
        }
        User user = userService.readByUsername(username);
        return user != null ? user.getId() : null;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
