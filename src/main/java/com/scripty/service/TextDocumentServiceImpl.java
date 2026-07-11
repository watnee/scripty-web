package com.scripty.service;

import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.commandmodel.textdocument.TextDocumentCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.textdocument.TextDocumentListViewModel;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TextDocumentServiceImpl implements TextDocumentService {

    private final TextDocumentRepository textDocumentRepository;
    private final ProjectRepository projectRepository;
    private final BlockRepository blockRepository;
    private final BlockService blockService;
    private final ProjectService projectService;
    private final ScriptImportTextExtractor scriptImportTextExtractor;
    private final ProjectActivityService projectActivityService;
    private final ScriptEditionService scriptEditionService;

    @Autowired
    public TextDocumentServiceImpl(TextDocumentRepository textDocumentRepository,
                                    ProjectRepository projectRepository,
                                    BlockRepository blockRepository,
                                    BlockService blockService,
                                    ProjectService projectService,
                                    ScriptImportTextExtractor scriptImportTextExtractor,
                                    ProjectActivityService projectActivityService,
                                    ScriptEditionService scriptEditionService) {
        this.textDocumentRepository = textDocumentRepository;
        this.projectRepository = projectRepository;
        this.blockRepository = blockRepository;
        this.blockService = blockService;
        this.projectService = projectService;
        this.scriptImportTextExtractor = scriptImportTextExtractor;
        this.projectActivityService = projectActivityService;
        this.scriptEditionService = scriptEditionService;
    }

    @Override
    public TextDocument read(Integer id) {
        return textDocumentRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public TextDocumentListViewModel getListViewModel(Integer projectId, User currentUser) {
        Project project = requireAccessibleProject(projectId, currentUser);
        if (project == null) {
            return null;
        }
        TextDocumentListViewModel vm = new TextDocumentListViewModel();
        vm.setProjectId(project.getId());
        vm.setProjectTitle(project.getTitle());
        List<TextDocumentViewModel> songs = new ArrayList<>();
        List<TextDocumentViewModel> drafts = new ArrayList<>();
        for (TextDocument doc : textDocumentRepository.findByProjectIdOrderBySortOrderAscUpdatedAtDesc(projectId)) {
            TextDocumentViewModel docVm = toViewModel(doc, project, false);
            if (TextDocument.TYPE_SONG.equalsIgnoreCase(doc.getDocumentType())) {
                songs.add(docVm);
            } else {
                drafts.add(docVm);
            }
        }
        vm.setSongs(songs);
        vm.setDrafts(drafts);
        return vm;
    }

    @Override
    @Transactional(readOnly = true)
    public TextDocumentViewModel getViewModel(Integer id, User currentUser) {
        TextDocument doc = textDocumentRepository.findById(id).orElse(null);
        if (doc == null || doc.getProject() == null) {
            return null;
        }
        if (!projectService.canUserAccessProject(doc.getProject().getId(), currentUser)) {
            return null;
        }
        return toViewModel(doc, doc.getProject(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public TextDocumentCommandModel getCommandModel(Integer id, User currentUser) {
        TextDocument doc = textDocumentRepository.findById(id).orElse(null);
        if (doc == null || doc.getProject() == null) {
            return null;
        }
        if (!projectService.canUserAccessProject(doc.getProject().getId(), currentUser)) {
            return null;
        }
        TextDocumentCommandModel cmd = new TextDocumentCommandModel();
        cmd.setId(doc.getId());
        cmd.setProjectId(doc.getProject().getId());
        cmd.setTitle(doc.getTitle());
        cmd.setDocumentType(doc.getDocumentType());
        cmd.setContent(doc.getContent() != null ? doc.getContent() : "");
        return cmd;
    }

    @Override
    public TextDocumentCommandModel getNewCommandModel(Integer projectId) {
        return getNewCommandModel(projectId, TextDocument.TYPE_SONG);
    }

    @Override
    public TextDocumentCommandModel getNewCommandModel(Integer projectId, String documentType) {
        TextDocumentCommandModel cmd = new TextDocumentCommandModel();
        cmd.setProjectId(projectId);
        cmd.setTitle("");
        if (documentType != null && TextDocument.TYPE_SONG.equalsIgnoreCase(documentType)) {
            cmd.setDocumentType(TextDocument.TYPE_SONG);
        } else {
            // Drafts section: notes (and legacy OTHER treated as drafts in the list).
            cmd.setDocumentType(TextDocument.TYPE_NOTES);
        }
        cmd.setContent("");
        return cmd;
    }

    @Override
    @Transactional
    public TextDocument save(TextDocumentCommandModel commandModel, User currentUser) {
        if (commandModel == null || commandModel.getProjectId() == null) {
            return null;
        }
        Project project = requireAccessibleProject(commandModel.getProjectId(), currentUser);
        if (project == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        TextDocument doc;
        boolean isNew = commandModel.getId() == null;
        if (!isNew) {
            doc = textDocumentRepository.findByIdAndProjectId(commandModel.getId(), commandModel.getProjectId())
                    .orElse(null);
            if (doc == null) {
                return null;
            }
        } else {
            doc = new TextDocument();
            doc.setProject(project);
            doc.setCreatedAt(now);
            doc.setSortOrder(textDocumentRepository.countByProjectId(project.getId()));
        }

        String title = PlainTextSanitizer.sanitizeSingleLine(
                commandModel.getTitle() != null ? commandModel.getTitle() : "");
        if (title == null || title.isEmpty()) {
            title = "Untitled";
        }
        doc.setTitle(title);
        doc.setDocumentType(normalizeDocumentType(commandModel.getDocumentType()));
        doc.setContent(PlainTextSanitizer.sanitize(
                commandModel.getContent() != null ? commandModel.getContent() : ""));
        doc.setUpdatedAt(now);

        project.setLastEdited(now);
        projectRepository.save(project);

        TextDocument saved = textDocumentRepository.save(doc);
        Integer actorId = currentUser != null ? currentUser.getId() : null;
        if (isNew) {
            projectActivityService.record(
                    project.getId(),
                    actorId,
                    ProjectActivity.ACTION_DOCUMENT_CREATED,
                    "created \"" + saved.getTitle() + "\"",
                    ProjectActivity.ENTITY_DOCUMENT,
                    saved.getId());
        } else {
            projectActivityService.record(
                    project.getId(),
                    actorId,
                    ProjectActivity.ACTION_DOCUMENT_UPDATED,
                    "updated \"" + saved.getTitle() + "\"",
                    ProjectActivity.ENTITY_DOCUMENT,
                    saved.getId());
        }
        return saved;
    }

    @Override
    @Transactional
    public TextDocument rename(Integer id, Integer projectId, String title, User currentUser) {
        if (id == null) {
            return null;
        }
        Project project = requireAccessibleProject(projectId, currentUser);
        if (project == null) {
            return null;
        }
        TextDocument doc = textDocumentRepository.findByIdAndProjectId(id, projectId).orElse(null);
        if (doc == null) {
            return null;
        }

        String newTitle = PlainTextSanitizer.sanitizeSingleLine(title != null ? title : "");
        if (newTitle == null || newTitle.isEmpty()) {
            newTitle = "Untitled";
        }
        if (newTitle.length() > 200) {
            newTitle = newTitle.substring(0, 200).trim();
        }
        String oldTitle = doc.getTitle();
        if (newTitle.equals(oldTitle)) {
            return doc;
        }

        LocalDateTime now = LocalDateTime.now();
        doc.setTitle(newTitle);
        doc.setUpdatedAt(now);
        project.setLastEdited(now);
        projectRepository.save(project);

        TextDocument saved = textDocumentRepository.save(doc);
        projectActivityService.record(
                project.getId(),
                currentUser != null ? currentUser.getId() : null,
                ProjectActivity.ACTION_DOCUMENT_UPDATED,
                "renamed \"" + oldTitle + "\" to \"" + saved.getTitle() + "\"",
                ProjectActivity.ENTITY_DOCUMENT,
                saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public void delete(Integer id, Integer projectId, User currentUser) {
        if (id == null || projectId == null || currentUser == null) {
            return;
        }
        if (!projectService.canUserAccessProject(projectId, currentUser)) {
            return;
        }
        TextDocument doc = textDocumentRepository.findByIdAndProjectId(id, projectId).orElse(null);
        if (doc == null) {
            return;
        }
        String title = doc.getTitle();
        Project project = doc.getProject();
        textDocumentRepository.delete(doc);
        if (project != null) {
            project.setLastEdited(LocalDateTime.now());
            projectRepository.save(project);
        }
        projectActivityService.record(
                projectId,
                currentUser.getId(),
                ProjectActivity.ACTION_DOCUMENT_DELETED,
                "deleted \"" + title + "\"",
                ProjectActivity.ENTITY_DOCUMENT,
                id);
    }

    @Override
    @Transactional
    public List<Block> insertIntoScript(Integer documentId, Integer afterBlockId, String asType, User currentUser) {
        TextDocument doc = textDocumentRepository.findById(documentId).orElse(null);
        if (doc == null || doc.getProject() == null) {
            return List.of();
        }

        Project project = doc.getProject();
        Integer projectId = project.getId();
        if (!projectService.canUserAccessProject(projectId, currentUser)
                || currentUser == null
                || !currentUser.isWriter()) {
            return List.of();
        }

        ScriptEdition edition = scriptEditionService.ensureDefaultEdition(projectId);
        // Ensure the active edition has at least one block to insert after.
        if (edition != null && blockRepository.countByScriptEditionId(edition.getId()) == 0) {
            blockService.createInitialBlock(projectId);
        } else if (edition == null && blockRepository.countByProjectId(projectId) == 0) {
            blockService.createInitialBlock(projectId);
        }

        Block afterBlock = null;
        if (afterBlockId != null) {
            afterBlock = blockRepository.findById(afterBlockId).orElse(null);
            if (afterBlock != null && !projectId.equals(afterBlock.getProject().getId())) {
                afterBlock = null;
            }
        }
        if (afterBlock == null) {
            List<Block> existing = edition != null
                    ? blockRepository.findByScriptEditionIdOrderByOrderAsc(edition.getId())
                    : blockRepository.findByProjectIdOrderByOrderAsc(projectId);
            if (existing.isEmpty()) {
                return List.of();
            }
            afterBlock = existing.get(existing.size() - 1);
        }

        String blockType = resolveInsertBlockType(doc.getDocumentType(), asType);
        List<CreateBlockBelowCommandModel> toInsert = splitContentIntoBlocks(doc, blockType);
        if (toInsert.isEmpty()) {
            return List.of();
        }

        List<Block> created = blockService.insertBlocksAfter(afterBlock.getId(), toInsert);
        project.setLastEdited(LocalDateTime.now());
        projectRepository.save(project);
        if (!created.isEmpty()) {
            projectActivityService.record(
                    projectId,
                    currentUser != null ? currentUser.getId() : null,
                    ProjectActivity.ACTION_DOCUMENT_INSERTED,
                    "inserted \"" + doc.getTitle() + "\" into the script",
                    ProjectActivity.ENTITY_DOCUMENT,
                    documentId);
        }
        return created;
    }

    @Override
    @Transactional
    public boolean syncInsertedBlocks(Integer documentId, User currentUser) {
        TextDocument doc = textDocumentRepository.findById(documentId).orElse(null);
        if (doc == null || doc.getProject() == null) {
            return false;
        }
        if (!projectService.canUserAccessProject(doc.getProject().getId(), currentUser)
                || currentUser == null
                || !currentUser.isWriter()) {
            return false;
        }
        return syncInsertedBlocks(doc, currentUser);
    }

    private boolean syncInsertedBlocks(TextDocument doc, User currentUser) {
        if (doc == null || doc.getId() == null || doc.getProject() == null) {
            return false;
        }
        if (currentUser == null || !currentUser.isWriter()) {
            return false;
        }
        Integer projectId = doc.getProject().getId();
        if (!projectService.canUserAccessProject(projectId, currentUser)) {
            return false;
        }
        String blockType = resolveInsertBlockType(doc.getDocumentType(), null);
        List<Block> existing = blockRepository.findBySourceDocumentIdOrderByOrderAsc(doc.getId());
        if (!existing.isEmpty() && existing.get(0).getType() != null) {
            // Preserve the type used at insert (e.g. draft inserted as Action).
            blockType = existing.get(0).getType();
        }
        List<CreateBlockBelowCommandModel> lines = splitContentIntoBlocks(doc, blockType);
        return blockService.replaceLinkedDocumentBlocks(projectId, doc.getId(), lines);
    }

    @Override
    @Transactional
    public TextDocument importFile(Integer projectId, String type, org.springframework.web.multipart.MultipartFile file, User currentUser)
            throws java.io.IOException {
        Project project = requireAccessibleProject(projectId, currentUser);
        if (project == null || file == null || file.isEmpty()) {
            return null;
        }

        String content = scriptImportTextExtractor.extractPlain(file);
        if (content == null) {
            content = "";
        }
        // Normalize and trim trailing blank lines so imports don't pad empty rows.
        content = content.replace("\r\n", "\n").replace('\r', '\n');
        while (content.endsWith("\n")) {
            content = content.substring(0, content.length() - 1);
        }

        TextDocumentCommandModel cmd = getNewCommandModel(projectId, type);
        cmd.setTitle(titleFromFilename(file.getOriginalFilename(), cmd.getDocumentType()));
        cmd.setContent(content);
        return save(cmd, currentUser);
    }

    private String titleFromFilename(String filename, String documentType) {
        String fallback = TextDocument.TYPE_SONG.equalsIgnoreCase(documentType) ? "Imported Song" : "Imported Note";
        if (filename == null || filename.isBlank()) {
            return fallback;
        }
        String name = filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.trim();
        if (name.isEmpty()) {
            return fallback;
        }
        if (name.length() > 200) {
            name = name.substring(0, 200).trim();
        }
        return name;
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

    private List<CreateBlockBelowCommandModel> splitContentIntoBlocks(TextDocument doc, String blockType) {
        List<CreateBlockBelowCommandModel> blocks = new ArrayList<>();
        String content = doc.getContent() != null ? doc.getContent() : "";
        String[] lines = content.split("\\R", -1);

        boolean hasNonEmpty = false;
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                hasNonEmpty = true;
                break;
            }
        }

        if (!hasNonEmpty) {
            return blocks;
        }

        for (String line : lines) {
            // Skip trailing empty lines only; keep blank lines in the middle as empty lyric/action rows.
            CreateBlockBelowCommandModel cmd = new CreateBlockBelowCommandModel();
            cmd.setContent(line != null ? line : "");
            cmd.setType(blockType);
            if (doc.getId() != null) {
                cmd.setSourceDocumentId(doc.getId());
            }
            blocks.add(cmd);
        }

        // Trim trailing empty lines so we don't pad the script with blank blocks.
        while (!blocks.isEmpty()) {
            String last = blocks.get(blocks.size() - 1).getContent();
            if (last == null || last.trim().isEmpty()) {
                blocks.remove(blocks.size() - 1);
            } else {
                break;
            }
        }
        // Trim leading empty lines similarly.
        while (!blocks.isEmpty()) {
            String first = blocks.get(0).getContent();
            if (first == null || first.trim().isEmpty()) {
                blocks.remove(0);
            } else {
                break;
            }
        }
        return blocks;
    }

    private String resolveInsertBlockType(String documentType, String asType) {
        if (asType != null && Block.ELEMENT_TYPES.contains(asType.toUpperCase())) {
            return asType.toUpperCase();
        }
        if (TextDocument.TYPE_SONG.equalsIgnoreCase(documentType)) {
            return Block.TYPE_LYRICS;
        }
        if (TextDocument.TYPE_NOTES.equalsIgnoreCase(documentType)) {
            return Block.TYPE_NOTE;
        }
        return Block.TYPE_ACTION;
    }

    private String normalizeDocumentType(String type) {
        if (type != null && TextDocument.DOCUMENT_TYPES.contains(type.toUpperCase())) {
            return type.toUpperCase();
        }
        return TextDocument.TYPE_SONG;
    }

    private TextDocumentViewModel toViewModel(TextDocument doc, Project project, boolean includeFullContent) {
        TextDocumentViewModel vm = new TextDocumentViewModel();
        vm.setId(doc.getId());
        if (project != null) {
            vm.setProjectId(project.getId());
            vm.setProjectTitle(project.getTitle());
        }
        vm.setTitle(doc.getTitle());
        vm.setDocumentType(doc.getDocumentType());
        vm.setDocumentTypeLabel(TextDocument.typeLabelFor(doc.getDocumentType()));
        vm.setSortOrder(doc.getSortOrder());
        vm.setCreatedAt(doc.getCreatedAt());
        vm.setUpdatedAt(doc.getUpdatedAt());
        String content = doc.getContent() != null ? doc.getContent() : "";
        if (includeFullContent) {
            vm.setContent(content);
        }
        vm.setPreview(buildPreview(content));
        return vm;
    }

    private String buildPreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        if (flat.length() <= 120) {
            return flat;
        }
        return flat.substring(0, 117).trim() + "…";
    }
}
