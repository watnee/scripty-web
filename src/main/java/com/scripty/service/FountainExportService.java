package com.scripty.service;

public interface FountainExportService {

    String exportProject(Integer projectId);

    String exportProject(Integer projectId, Integer editionId);

    /** Honors the caller's per-type auto-capitalization preferences. */
    String exportProject(Integer projectId, Integer editionId, CapitalizationPreferences caps);
}
