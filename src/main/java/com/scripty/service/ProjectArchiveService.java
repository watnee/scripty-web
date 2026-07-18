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
     * Bundle several projects into a single .scripty.json file so a "select all"
     * download returns one file that {@link #importProjects} can read back.
     * Unknown or missing projects are skipped; returns {@code null} when none of
     * the ids resolve to a project.
     */
    byte[] exportProjectsBundle(List<Integer> projectIds);

    /**
     * Create brand-new projects from an archive file — one project for a
     * single-project file, several for a bundle. Never returns an empty list.
     *
     * @throws ScriptImportException with a user-facing message when the file is
     *         missing, not a Scripty project file, or from a newer format version.
     */
    List<Project> importProjects(MultipartFile file) throws ScriptImportException;
}
