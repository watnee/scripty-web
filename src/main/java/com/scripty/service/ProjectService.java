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
     * Moves the project to trash (soft delete). Trashed projects are hidden everywhere
     * and permanently purged after {@link ProjectServiceImpl#TRASH_RETENTION_DAYS} days.
     */
    Project deleteProject(Integer id);

    Project restoreProject(Integer id);

    Project deleteProjectPermanently(Integer id);

    /**
     * Trashed projects visible to the user, most recently deleted first.
     */
    List<com.scripty.viewmodel.project.projectlist.ProjectViewModel> getTrashedProjectViewModels(User user);

    /**
     * Permanently deletes projects trashed more than the retention period ago.
     * @return number of projects purged
     */
    int purgeExpiredTrash();

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
