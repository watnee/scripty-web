package com.scripty.service;

import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.TextDocument;
import com.scripty.dto.TextDocumentFolder;
import com.scripty.dto.User;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentFolderRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.util.PlainTextSanitizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TextDocumentFolderServiceImpl implements TextDocumentFolderService {

    private final TextDocumentFolderRepository folderRepository;
    private final TextDocumentRepository textDocumentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectActivityService projectActivityService;

    @Autowired
    public TextDocumentFolderServiceImpl(TextDocumentFolderRepository folderRepository,
                                         TextDocumentRepository textDocumentRepository,
                                         ProjectRepository projectRepository,
                                         ProjectService projectService,
                                         ProjectActivityService projectActivityService) {
        this.folderRepository = folderRepository;
        this.textDocumentRepository = textDocumentRepository;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectActivityService = projectActivityService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TextDocumentFolder> list(Integer projectId, String documentType, User currentUser) {
        if (requireAccessibleProject(projectId, currentUser) == null) {
            return null;
        }
        return folderRepository.findByProjectIdAndDocumentTypeOrderByNameAsc(
                projectId, normalizeType(documentType));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TextDocumentFolder> listAll(Integer projectId, User currentUser) {
        if (requireAccessibleProject(projectId, currentUser) == null) {
            return null;
        }
        return folderRepository.findByProjectIdOrderByDocumentTypeAscNameAsc(projectId);
    }

    @Override
    @Transactional
    public TextDocumentFolder create(Integer projectId, String documentType, String name, User currentUser) {
        Project project = requireAccessibleProject(projectId, currentUser);
        if (project == null) {
            return null;
        }
        String type = normalizeType(documentType);
        String cleanName = requireName(name);
        requireNameFree(projectId, type, cleanName, null);

        LocalDateTime now = LocalDateTime.now();
        TextDocumentFolder folder = new TextDocumentFolder();
        folder.setProject(project);
        folder.setDocumentType(type);
        folder.setName(cleanName);
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);
        TextDocumentFolder saved = folderRepository.save(folder);
        record(projectId, currentUser, "added the folder \"" + saved.getName() + "\"");
        return saved;
    }

    @Override
    @Transactional
    public TextDocumentFolder rename(Integer id, Integer projectId, String name, User currentUser) {
        if (requireAccessibleProject(projectId, currentUser) == null) {
            return null;
        }
        TextDocumentFolder folder = folderRepository.findByIdAndProjectId(id, projectId).orElse(null);
        if (folder == null) {
            return null;
        }
        String cleanName = requireName(name);
        // A folder renamed to what it is already called is not a clash with
        // itself, and is not worth refusing.
        if (cleanName.equals(folder.getName())) {
            return folder;
        }
        requireNameFree(projectId, folder.getDocumentType(), cleanName, folder.getId());

        String was = folder.getName();
        folder.setName(cleanName);
        folder.setUpdatedAt(LocalDateTime.now());
        TextDocumentFolder saved = folderRepository.save(folder);
        record(projectId, currentUser, "renamed the folder \"" + was + "\" to \"" + cleanName + "\"");
        return saved;
    }

    @Override
    @Transactional
    public int delete(Integer id, Integer projectId, User currentUser) {
        if (requireAccessibleProject(projectId, currentUser) == null) {
            return -1;
        }
        TextDocumentFolder folder = folderRepository.findByIdAndProjectId(id, projectId).orElse(null);
        if (folder == null) {
            return -1;
        }
        // Unfiled here rather than left to the column's ON DELETE SET NULL:
        // Hibernate holds these rows in its own first-level cache, and a
        // database-side change it did not make is a change it does not see —
        // the list rendered in the same request would still show them under a
        // folder that no longer exists.
        List<TextDocument> held = textDocumentRepository.findByFolder_Id(folder.getId());
        for (TextDocument document : held) {
            document.setFolder(null);
            textDocumentRepository.save(document);
        }
        String was = folder.getName();
        folderRepository.delete(folder);
        record(projectId, currentUser, "removed the folder \"" + was + "\"");
        return held.size();
    }

    @Override
    @Transactional
    public TextDocument moveDocument(Integer documentId, Integer projectId, Integer folderId, User currentUser) {
        if (documentId == null || requireAccessibleProject(projectId, currentUser) == null) {
            return null;
        }
        TextDocument document = textDocumentRepository
                .findByIdAndProjectIdAndDeletedAtIsNull(documentId, projectId).orElse(null);
        if (document == null) {
            return null;
        }
        TextDocumentFolder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndProjectId(folderId, projectId).orElse(null);
            if (folder == null || !sameList(folder, document)) {
                // A folder from the other list is not a folder this document
                // can go in. Refused rather than quietly unfiled: the caller
                // asked for somewhere specific.
                return null;
            }
        }
        applyFolder(document, folder);
        record(projectId, currentUser, folder == null
                ? "took \"" + document.getTitle() + "\" out of its folder"
                : "moved \"" + document.getTitle() + "\" into \"" + folder.getName() + "\"");
        return document;
    }

    @Override
    @Transactional
    public int moveDocuments(List<Integer> documentIds, Integer projectId, Integer folderId, User currentUser) {
        if (documentIds == null || documentIds.isEmpty()
                || requireAccessibleProject(projectId, currentUser) == null) {
            return 0;
        }
        TextDocumentFolder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndProjectId(folderId, projectId).orElse(null);
            if (folder == null) {
                return 0;
            }
        }
        List<TextDocument> moved = new ArrayList<>();
        for (Integer id : new LinkedHashSet<>(documentIds)) {
            if (id == null) {
                continue;
            }
            TextDocument document = textDocumentRepository
                    .findByIdAndProjectIdAndDeletedAtIsNull(id, projectId).orElse(null);
            // A selection made before someone else changed a song into a note
            // can hold a document this folder cannot take; skip it rather than
            // refusing the whole move.
            if (document == null || (folder != null && !sameList(folder, document))) {
                continue;
            }
            applyFolder(document, folder);
            moved.add(document);
        }
        if (!moved.isEmpty()) {
            record(projectId, currentUser, folder == null
                    ? "took " + counted(moved.size()) + " out of their folders"
                    : "moved " + counted(moved.size()) + " into \"" + folder.getName() + "\"");
        }
        return moved.size();
    }

    /** Writes the move and dates the project, the way every document write does. */
    private void applyFolder(TextDocument document, TextDocumentFolder folder) {
        document.setFolder(folder);
        // Deliberately not touched: updatedAt. Filing a song says nothing about
        // when it was last written in, and the list can be sorted by that —
        // tidying a project should not send every song it touches to the top.
        textDocumentRepository.save(document);
        Project project = document.getProject();
        if (project != null) {
            project.setLastEdited(LocalDateTime.now());
            projectRepository.save(project);
        }
    }

    /** Whether a folder belongs to the list this document is in. */
    private boolean sameList(TextDocumentFolder folder, TextDocument document) {
        return normalizeType(folder.getDocumentType()).equals(normalizeType(document.getDocumentType()));
    }

    /**
     * SONG or NOTES, and nothing else.
     *
     * <p>Notes are "everything that is not a song" on this screen — the legacy
     * OTHER type is listed and filed with them — so anything unrecognised lands
     * there rather than being refused. That is the same rule the list itself
     * follows when it splits a project's documents in two.
     */
    private String normalizeType(String type) {
        return TextDocument.TYPE_SONG.equalsIgnoreCase(type)
                ? TextDocument.TYPE_SONG
                : TextDocument.TYPE_NOTES;
    }

    private String requireName(String name) {
        String clean = PlainTextSanitizer.sanitizeSingleLine(name != null ? name : "");
        clean = clean != null ? clean.trim() : "";
        if (clean.isEmpty()) {
            throw new DocumentFolderException("Give the folder a name.");
        }
        if (clean.length() > TextDocumentFolder.NAME_MAX_LENGTH) {
            clean = clean.substring(0, TextDocumentFolder.NAME_MAX_LENGTH).trim();
        }
        return clean;
    }

    private void requireNameFree(Integer projectId, String type, String name, Integer allowedId) {
        folderRepository.findByProjectIdAndDocumentTypeAndNameIgnoreCase(projectId, type, name)
                .filter(existing -> !existing.getId().equals(allowedId))
                .ifPresent(existing -> {
                    throw new DocumentFolderException(
                            "There is already a folder called “" + existing.getName() + "” here.");
                });
    }

    private String counted(int n) {
        return n + (n == 1 ? " document" : " documents");
    }

    private void record(Integer projectId, User currentUser, String summary) {
        projectActivityService.record(
                projectId,
                currentUser != null ? currentUser.getId() : null,
                ProjectActivity.ACTION_DOCUMENT_UPDATED,
                summary,
                ProjectActivity.ENTITY_DOCUMENT,
                null);
    }

    private Project requireAccessibleProject(Integer projectId, User currentUser) {
        if (projectId == null || currentUser == null) {
            return null;
        }
        Project project = projectRepository.findWithTeamsById(projectId).orElse(null);
        if (project == null || !projectService.canUserAccessProject(project, currentUser)) {
            return null;
        }
        return project;
    }
}
