package com.scripty.service;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.commandmodel.project.titlepage.TitlePageCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectShareUserViewModel;
import com.scripty.viewmodel.user.userprofile.UserProjectAccessViewModel;
import java.util.List;
import java.util.Map;

public interface ProjectService {

    Project read(Integer id);
    Project readWithTeams(Integer id);
    Project getProjectByBlock(Block block);

    /**
     * Whether the user may access the project (privileged roles, matching team, or unassigned project).
     */
    boolean canUserAccessProject(Integer projectId, User user);

    boolean canUserAccessProject(Project project, User user);

    ProjectListViewModel getProjectListViewModel();
    ProjectListViewModel getProjectListViewModel(String userTeam);
    ProjectProfileViewModel getProjectProfileViewModel(Integer id);
    ProjectProfileViewModel getProjectProfileViewModel(Integer id, Integer editionId);
    ProjectProfileViewModel getProjectProfileViewModel(Integer id, Integer editionId, boolean canBrowseEditions);

    CreateProjectViewModel getCreateProjectViewModel();
    EditProjectViewModel getEditProjectViewModel(Integer id);

    Project saveCreateProjectCommandModel(CreateProjectCommandModel createProjectCommandModel);
    Project saveEditProjectCommandModel(EditProjectCommandModel editProjectCommandModel);
    TitlePageCommandModel getTitlePageCommandModel(Integer id);
    Project saveTitlePageCommandModel(TitlePageCommandModel titlePageCommandModel);

    /**
     * Moves the project to the trash. The row and all its content survive until
     * {@link ProjectPurgeService} purges it after the recovery window.
     */
    Project deleteProject(Integer id);

    /** Trashed projects, most recently deleted first. Admin-only. */
    List<Project> getTrashedProjects();

    /** How many projects are currently in the trash. Admin-only. */
    long getTrashedProjectCount();

    /** A single trashed project, or null if it is not in the trash. Admin-only. */
    Project getTrashedProject(Integer id);

    /** Returns false if the project is not in the trash (already restored, or purged). */
    boolean restoreProject(Integer id);

    /** Restores each id still in the trash; returns how many were restored. Admin-only. */
    int restoreProjects(List<Integer> ids);

    /** Restores every trashed project; returns how many were restored. Admin-only. */
    int restoreAllTrashed();

    /**
     * Immediately and permanently deletes trashed projects, skipping the
     * retention window. Only projects actually in the trash are removed.
     * Returns how many were purged. Admin-only.
     */
    int purgeProjects(List<Integer> ids);

    void setProjectTeams(Integer projectId, java.util.List<Integer> teamIds);

    List<ProjectShareUserViewModel> getProjectShareAccessUsers(Integer projectId);

    /**
     * Effective project access for a user (view vs edit), for admin profile display.
     */
    List<UserProjectAccessViewModel> getUserProjectAccess(User user);

    /**
     * Batch project access for many users (loads projects once). Keyed by user id.
     */
    Map<Integer, List<UserProjectAccessViewModel>> getUsersProjectAccess(List<User> users);
}
