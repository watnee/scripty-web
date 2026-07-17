package com.scripty.service;

import com.scripty.dto.SongEdition;
import com.scripty.viewmodel.song.edition.SongEditionViewModel;
import java.util.List;

/**
 * Named, switchable versions of a song, mirroring {@link ScriptEditionService}
 * for screenplays. The container here is the song's {@link com.scripty.dto.TextDocument}
 * rather than a project.
 */
public interface SongEditionService {

    SongEdition read(Integer id);

    SongEdition requireForDocument(Integer documentId, Integer editionId);

    /**
     * Resolves which edition a user may load. Writers/admins may open any
     * edition; everyone else is locked to the published edition (falling back to
     * the default when none is published).
     */
    SongEdition resolveForAccess(Integer documentId, Integer editionId, boolean canBrowseEditions);

    SongEdition getDefaultForDocument(Integer documentId);

    SongEdition getPublishedForDocument(Integer documentId);

    SongEdition ensureDefaultEdition(Integer documentId);

    List<SongEdition> listForDocument(Integer documentId);

    List<SongEditionViewModel> getEditionViewModels(Integer documentId);

    /** Edition list for the UI. Non-browsers only see the published (or default) edition. */
    List<SongEditionViewModel> getEditionViewModels(Integer documentId, boolean canBrowseEditions);

    SongEdition createEdition(Integer documentId, String name, Integer copyFromEditionId);

    boolean renameEdition(Integer editionId, Integer documentId, String name);

    boolean deleteEdition(Integer editionId, Integer documentId);

    boolean setDefaultEdition(Integer editionId, Integer documentId);

    boolean setPublishedEdition(Integer editionId, Integer documentId);

    void touchEdition(SongEdition edition);
}
