package com.scripty.service;

public interface PdfExportService {

    byte[] exportProject(Integer projectId);

    byte[] exportProject(Integer projectId, Integer editionId);
}
