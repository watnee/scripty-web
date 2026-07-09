package com.scripty.service;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface FountainImportService {

    /**
     * Outcome of a file import for flash messaging.
     */
    record ImportOutcome(boolean success, String message) {
        static ImportOutcome ok(String message) {
            return new ImportOutcome(true, message);
        }

        static ImportOutcome fail(String message) {
            return new ImportOutcome(false, message);
        }
    }

    void importIntoProject(Integer projectId, String fountainText);

    void importFileIntoProject(Integer projectId, MultipartFile file) throws IOException;

    /**
     * Import a file and return a user-facing status message (success or failure).
     * Does not throw for empty/corrupt content; throws only for unexpected I/O.
     */
    ImportOutcome importFileIntoProjectWithStatus(Integer projectId, MultipartFile file) throws IOException;
}
