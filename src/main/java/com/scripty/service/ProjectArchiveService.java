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

    /**
     * Read an archive back into a project that already exists, rather than into
     * a new one: the screenplay keeps its id, its teams, its collaborators and
     * its history, and the words in it become the ones in the file.
     *
     * <p>This is the return half of a copy kept elsewhere. A device that writes
     * without an account hands a copy to the account when it signs in, goes on
     * writing in its own copy while signed out, and comes back with words the
     * account has never seen — {@link #importProjects} could only file those as
     * a second screenplay.
     *
     * <p>Nothing is lost on the way: the script as it stands is saved to the
     * version history first, and the songs and notes being replaced go to the
     * document trash rather than out of the database. Only the project's default
     * edition is touched — other drafts are left exactly as they are, and the
     * file's own editions are ignored for the same reason.
     *
     * @return the project, or {@code null} when the id names no live project.
     * @throws ScriptImportException with a user-facing message when the file is
     *         missing, not a Scripty project file, or from a newer format version.
     */
    Project replaceProject(Integer projectId, MultipartFile file) throws ScriptImportException;
}
