package com.scripty.service;

public interface FdxExportService {

    byte[] exportProject(Integer projectId);

    byte[] exportProject(Integer projectId, Integer editionId);

    /** Honors the caller's per-type auto-capitalization preferences. */
    byte[] exportProject(Integer projectId, Integer editionId, CapitalizationPreferences caps);
}
