package com.scripty.service;

import com.scripty.dto.ScriptEdition;
import com.scripty.viewmodel.project.edition.ScriptEditionViewModel;
import java.util.List;

public interface ScriptEditionService {

    ScriptEdition read(Integer id);

    ScriptEdition requireForProject(Integer projectId, Integer editionId);

    /**
     * Resolves which edition a user may load.
     * Writers/admins may open any edition; everyone else is locked to the published edition
     * (falling back to the default edition when none is published).
     */
    ScriptEdition resolveForAccess(Integer projectId, Integer editionId, boolean canBrowseEditions);

    ScriptEdition getDefaultForProject(Integer projectId);

    ScriptEdition getPublishedForProject(Integer projectId);

    ScriptEdition ensureDefaultEdition(Integer projectId);

    List<ScriptEdition> listForProject(Integer projectId);

    List<ScriptEditionViewModel> getEditionViewModels(Integer projectId);

    /**
     * Edition list for the UI. Non-browsers only see the published (or default) edition.
     */
    List<ScriptEditionViewModel> getEditionViewModels(Integer projectId, boolean canBrowseEditions);

    ScriptEdition createEdition(Integer projectId, String name, Integer copyFromEditionId);

    boolean renameEdition(Integer editionId, Integer projectId, String name);

    boolean deleteEdition(Integer editionId, Integer projectId);

    boolean setDefaultEdition(Integer editionId, Integer projectId);

    boolean setPublishedEdition(Integer editionId, Integer projectId);

    void touchEdition(ScriptEdition edition);
}
