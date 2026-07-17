package com.scripty.service;

import com.scripty.dto.Project;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Full-project export/import as a portable .scripty.json file: title page,
 * screenplay editions, characters, blocks, and text documents.
 */
public interface ProjectArchiveService {

    /** Serialize the whole project (all editions) to archive JSON bytes. */
    byte[] exportProject(Integer projectId);

    /**
     * Bundle several projects' .scripty.json archives into a single ZIP so a
     * "select all" download returns one file. Unknown or missing projects are
     * skipped; returns {@code null} when none of the ids resolve to a project.
     */
    byte[] exportProjectsZip(List<Integer> projectIds);

    /**
     * Create a brand-new project from an archive file.
     *
     * @throws ScriptImportException with a user-facing message when the file is
     *         missing, not a Scripty project file, or from a newer format version.
     */
    Project importProject(MultipartFile file) throws ScriptImportException;
}
