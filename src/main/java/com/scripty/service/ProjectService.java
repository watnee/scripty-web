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

    /** Every trashed project, most recently deleted first. Not access-scoped. */
    List<Project> getTrashedProjects();

    /**
     * Trashed projects the user is allowed to see, most recently deleted first.
     * Applies the same team rule as the live project list, so a user never sees
     * the title of a project they could not have opened.
     */
    List<Project> getTrashedProjects(User user);

    /** A single trashed project, or null if it is not in the trash. Not access-scoped. */
    Project getTrashedProject(Integer id);

    /**
     * A trashed project the user is allowed to act on, or null if it is not in
     * the trash or is out of their reach.
     */
    Project getTrashedProject(Integer id, User user);

    /** Returns false if the project is not in the trash (already restored, or purged). */
    boolean restoreProject(Integer id);

    /**
     * Hard-deletes a trashed project and everything under it. Returns false if
     * the project is not in the trash. Cannot be undone.
     */
    boolean purgeProject(Integer id);

    /**
     * Hard-deletes every trashed project the user can see. Returns how many went.
     */
    int emptyTrash(User user);

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
