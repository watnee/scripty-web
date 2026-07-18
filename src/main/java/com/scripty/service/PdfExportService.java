package com.scripty.service;

import com.scripty.dto.PageSetup;

public interface PdfExportService {

    byte[] exportProject(Integer projectId);

    byte[] exportProject(Integer projectId, Integer editionId);

    /** Honors the caller's per-type auto-capitalization preferences. */
    byte[] exportProject(Integer projectId, Integer editionId, CapitalizationPreferences caps);

    byte[] exportProject(Integer projectId, Integer editionId, PageSetup pageSetup);

    /** Honors both the auto-capitalization preferences and the paper/margin setup. */
    byte[] exportProject(Integer projectId,
                         Integer editionId,
                         CapitalizationPreferences caps,
                         PageSetup pageSetup);
}
