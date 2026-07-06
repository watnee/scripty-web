package com.scripty.service;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface FountainImportService {

    void importIntoProject(Integer projectId, String fountainText);

    void importFileIntoProject(Integer projectId, MultipartFile file) throws IOException;
}
