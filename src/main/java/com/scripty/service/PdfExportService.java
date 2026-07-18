package com.scripty.service;

import com.scripty.dto.PageSetup;

public interface PdfExportService {

    byte[] exportProject(Integer projectId);

    byte[] exportProject(Integer projectId, Integer editionId);

    byte[] exportProject(Integer projectId, Integer editionId, PageSetup pageSetup);
}
