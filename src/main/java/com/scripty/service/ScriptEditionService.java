package com.scripty.service;

import com.scripty.dto.ScriptEdition;
import com.scripty.viewmodel.project.edition.ScriptEditionViewModel;
import java.util.List;

public interface ScriptEditionService {

    ScriptEdition read(Integer id);

    ScriptEdition requireForProject(Integer projectId, Integer editionId);

    ScriptEdition getDefaultForProject(Integer projectId);

    ScriptEdition ensureDefaultEdition(Integer projectId);

    List<ScriptEdition> listForProject(Integer projectId);

    List<ScriptEditionViewModel> getEditionViewModels(Integer projectId);

    ScriptEdition createEdition(Integer projectId, String name, Integer copyFromEditionId);

    boolean renameEdition(Integer editionId, Integer projectId, String name);

    boolean deleteEdition(Integer editionId, Integer projectId);

    boolean setDefaultEdition(Integer editionId, Integer projectId);

    void touchEdition(ScriptEdition edition);
}
