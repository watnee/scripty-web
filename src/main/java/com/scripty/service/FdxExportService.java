package com.scripty.service;

public interface FdxExportService {

    byte[] exportProject(Integer projectId);

    byte[] exportProject(Integer projectId, Integer editionId);
}
