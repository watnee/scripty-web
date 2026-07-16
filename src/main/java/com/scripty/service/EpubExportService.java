package com.scripty.service;

public interface EpubExportService {

    byte[] exportProject(Integer projectId);

    byte[] exportProject(Integer projectId, Integer editionId);
}
