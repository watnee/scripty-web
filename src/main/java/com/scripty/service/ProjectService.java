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
import com.scripty.viewmodel.project.projecttrash.ProjectTrashViewModel;
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
     * Moves a screenplay to the trash. Its scenes, versions, editions, and documents stay put
     * so a restore brings the whole thing back; the row is removed for good only by
     * {@link #purgeProject} or, after the retention window, {@link #purgeExpiredProjects}.
     */
    Project deleteProject(Integer id);

    ProjectTrashViewModel getProjectTrashViewModel(User currentUser);

    Project restoreProject(Integer id, User currentUser);

    /** Permanently deletes a trashed screenplay. Reachable only from the trash. */
    boolean purgeProject(Integer id, User currentUser);

    /** Permanently deletes trashed screenplays past the retention window. For the nightly job. */
    int purgeExpiredProjects();

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
