package com.scripty.service;

public interface FountainExportService {

    String exportProject(Integer projectId);

    String exportProject(Integer projectId, Integer editionId);
}
