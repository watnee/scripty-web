package com.scripty.service;

public interface DocxExportService {

    byte[] exportProject(Integer projectId);

    byte[] exportProject(Integer projectId, Integer editionId);
}
