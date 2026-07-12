package com.scripty.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.ScriptEdition;
import com.scripty.dto.TextDocument;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ScriptEditionRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.util.PlainTextSanitizer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectArchiveServiceImpl implements ProjectArchiveService {

    private static final String BAD_FILE_MESSAGE =
            "That file isn't a Scripty project file. Choose a .scripty.json file exported from Scripty.";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScriptEditionRepository scriptEditionRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private TextDocumentRepository textDocumentRepository;

    @Autowired
    private ScriptEditionService scriptEditionService;

    @Autowired
    private ProjectVersionService projectVersionService;

    @Autowired
    private ProjectActivityService projectActivityService;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return null;
        }

        ProjectArchive archive = new ProjectArchive();
        archive.format = ProjectArchive.FORMAT;
        archive.formatVersion = ProjectArchive.CURRENT_VERSION;
        archive.exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        ProjectArchive.Info info = new ProjectArchive.Info();
        info.title = project.getTitle();
        info.screenplayTitle = project.getScreenplayTitle();
        info.writers = project.getWriters();
        info.contactInfo = project.getContactInfo();
        info.screenplayVersion = project.getScreenplayVersion();
        archive.project = info;

        for (ScriptEdition edition : scriptEditionRepository.findByProjectIdOrderByNameAsc(projectId)) {
            ProjectArchive.Edition entry = new ProjectArchive.Edition();
            entry.key = edition.getId();
            entry.name = edition.getName();
            entry.defaultEdition = edition.isDefault();
            entry.published = edition.isPublished();
            archive.editions.add(entry);
        }

        for (Person person : personRepository.findByProjectIdOrderByNameAsc(projectId)) {
            ProjectArchive.Character entry = new ProjectArchive.Character();
            entry.key = person.getId();
            entry.name = person.getName();
            entry.fullName = person.getFullName();
            entry.editionKey = person.getScriptEdition() != null ? person.getScriptEdition().getId() : null;
            archive.characters.add(entry);
        }

        for (TextDocument document : textDocumentRepository.findByProjectIdOrderBySortOrderAscUpdatedAtDesc(projectId)) {
            ProjectArchive.Document entry = new ProjectArchive.Document();
            entry.key = document.getId();
            entry.title = document.getTitle();
            entry.documentType = document.getDocumentType();
            entry.content = document.getContent();
            entry.sortOrder = document.getSortOrder();
            archive.documents.add(entry);
        }

        for (Block block : blockRepository.findByProjectIdOrderByOrderAsc(projectId)) {
            ProjectArchive.BlockEntry entry = new ProjectArchive.BlockEntry();
            entry.order = block.getOrder();
            entry.type = block.getType();
            entry.content = block.getContent();
            entry.sceneDelimiter = block.isSceneDelimiter();
            entry.textAlign = block.getTextAlign();
            entry.textBold = block.isTextBold();
            entry.textItalic = block.isTextItalic();
            entry.textUnderline = block.isTextUnderline();
            entry.bookmarked = block.isBookmarked();
            entry.pinned = block.isPinned();
            entry.tags = block.getTags();
            entry.editionKey = block.getScriptEdition() != null ? block.getScriptEdition().getId() : null;
            entry.characterKey = block.getPerson() != null ? block.getPerson().getId() : null;
            entry.sourceDocumentKey = block.getSourceDocumentId();
            archive.blocks.add(entry);
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(archive);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize project " + projectId, e);
        }
    }

    @Override
    @Transactional
    public Project importProject(MultipartFile file) throws ScriptImportException {
        if (file == null || file.isEmpty()) {
            throw new ScriptImportException("No file selected. Choose a .scripty.json file exported from Scripty.");
        }

        ProjectArchive archive;
        try {
            archive = objectMapper.readValue(file.getBytes(), ProjectArchive.class);
        } catch (IOException e) {
            throw new ScriptImportException(BAD_FILE_MESSAGE, e);
        }
        if (archive == null || !ProjectArchive.FORMAT.equals(archive.format)) {
            throw new ScriptImportException(BAD_FILE_MESSAGE);
        }
        if (archive.formatVersion < 1) {
            throw new ScriptImportException(BAD_FILE_MESSAGE);
        }
        if (archive.formatVersion > ProjectArchive.CURRENT_VERSION) {
            throw new ScriptImportException(
                    "That project file was exported by a newer version of Scripty and can't be imported here.");
        }

        LocalDateTime now = LocalDateTime.now();
        Project project = new Project();
        ProjectArchive.Info info = archive.project != null ? archive.project : new ProjectArchive.Info();
        String title = clean(info.title, 100);
        project.setTitle(title != null && !title.isBlank() ? title : "Imported Project");
        project.setScreenplayTitle(clean(info.screenplayTitle, 255));
        project.setWriters(clean(info.writers, 255));
        project.setContactInfo(truncate(PlainTextSanitizer.sanitize(info.contactInfo), 1000));
        project.setScreenplayVersion(clean(info.screenplayVersion, 255));
        project.setLastEdited(now);
        project = projectRepository.save(project);

        Map<Integer, ScriptEdition> editionsByKey = new HashMap<>();
        ScriptEdition defaultEdition = null;
        List<ProjectArchive.Edition> editionEntries = archive.editions != null ? archive.editions : List.of();
        boolean hasDefault = editionEntries.stream().anyMatch(e -> e != null && e.defaultEdition);
        boolean hasPublished = editionEntries.stream().anyMatch(e -> e != null && e.published);
        boolean first = true;
        for (ProjectArchive.Edition entry : editionEntries) {
            if (entry == null) {
                continue;
            }
            ScriptEdition edition = new ScriptEdition();
            edition.setProject(project);
            String name = clean(entry.name, 100);
            edition.setName(name != null && !name.isBlank() ? name : "Original");
            // The file may disagree with our invariants (exactly one default,
            // at least one published edition) — repair rather than reject.
            boolean isDefault = hasDefault ? entry.defaultEdition && defaultEdition == null : first;
            edition.setDefault(isDefault);
            edition.setPublished(hasPublished ? entry.published : isDefault);
            edition.setCreatedAt(now);
            edition.setUpdatedAt(now);
            edition.setLastEdited(now);
            edition = scriptEditionRepository.save(edition);
            if (isDefault) {
                defaultEdition = edition;
            }
            if (entry.key != null) {
                editionsByKey.put(entry.key, edition);
            }
            first = false;
        }
        if (defaultEdition == null) {
            defaultEdition = scriptEditionService.ensureDefaultEdition(project.getId());
        }

        Map<Integer, TextDocument> documentsByKey = new HashMap<>();
        int documentSequence = 0;
        if (archive.documents != null) {
            for (ProjectArchive.Document entry : archive.documents) {
                if (entry == null) {
                    continue;
                }
                TextDocument document = new TextDocument();
                document.setProject(project);
                String docTitle = clean(entry.title, 200);
                document.setTitle(docTitle != null && !docTitle.isBlank() ? docTitle : "Untitled");
                String docType = entry.documentType != null ? entry.documentType.trim().toUpperCase() : null;
                document.setDocumentType(
                        docType != null && TextDocument.DOCUMENT_TYPES.contains(docType)
                                ? docType
                                : TextDocument.TYPE_OTHER);
                document.setContent(PlainTextSanitizer.sanitize(entry.content));
                document.setSortOrder(entry.sortOrder != null ? entry.sortOrder : documentSequence);
                document.setCreatedAt(now);
                document.setUpdatedAt(now);
                document = textDocumentRepository.save(document);
                if (entry.key != null) {
                    documentsByKey.put(entry.key, document);
                }
                documentSequence++;
            }
        }

        Map<Integer, Person> charactersByKey = new HashMap<>();
        if (archive.characters != null) {
            for (ProjectArchive.Character entry : archive.characters) {
                if (entry == null) {
                    continue;
                }
                Person person = new Person();
                person.setProject(project);
                person.setScriptEdition(resolveEdition(entry.editionKey, editionsByKey, defaultEdition));
                String name = clean(entry.name, 60);
                person.setName(name != null && !name.isBlank() ? name : "UNNAMED");
                String fullName = clean(entry.fullName, 60);
                person.setFullName(fullName != null && !fullName.isBlank() ? fullName : person.getName());
                person = personRepository.save(person);
                if (entry.key != null) {
                    charactersByKey.put(entry.key, person);
                }
            }
        }

        if (archive.blocks != null) {
            // Re-number per edition so orders are dense and unique even if the
            // file's order values are missing or collide.
            Map<Integer, List<ProjectArchive.BlockEntry>> blocksByEdition = new LinkedHashMap<>();
            for (ProjectArchive.BlockEntry entry : archive.blocks) {
                if (entry == null) {
                    continue;
                }
                blocksByEdition.computeIfAbsent(entry.editionKey, k -> new ArrayList<>()).add(entry);
            }
            for (List<ProjectArchive.BlockEntry> group : blocksByEdition.values()) {
                group.sort(Comparator.comparing(e -> e.order != null ? e.order : Integer.MAX_VALUE));
                int order = 1;
                for (ProjectArchive.BlockEntry entry : group) {
                    Block block = new Block();
                    block.setProject(project);
                    block.setScriptEdition(resolveEdition(entry.editionKey, editionsByKey, defaultEdition));
                    block.setOrder(order++);
                    String content = PlainTextSanitizer.sanitize(entry.content);
                    block.setContent(content != null ? content : "");
                    String type = entry.type != null ? entry.type.trim().toUpperCase() : null;
                    block.setType(type != null && Block.ELEMENT_TYPES.contains(type) ? type : Block.TYPE_ACTION);
                    block.setSceneDelimiter(entry.sceneDelimiter);
                    String align = entry.textAlign != null ? entry.textAlign.trim().toUpperCase() : null;
                    block.setTextAlign(align != null && Block.TEXT_ALIGNS.contains(align) ? align : null);
                    block.setTextBold(entry.textBold);
                    block.setTextItalic(entry.textItalic);
                    block.setTextUnderline(entry.textUnderline);
                    block.setBookmarked(entry.bookmarked);
                    block.setPinned(entry.pinned);
                    block.setTags(clean(entry.tags, 255));
                    if (entry.characterKey != null) {
                        block.setPerson(charactersByKey.get(entry.characterKey));
                    }
                    if (entry.sourceDocumentKey != null) {
                        TextDocument source = documentsByKey.get(entry.sourceDocumentKey);
                        block.setSourceDocumentId(source != null ? source.getId() : null);
                    }
                    blockRepository.save(block);
                }
            }
        }

        if (defaultEdition != null) {
            projectVersionService.autoSaveVersion(project.getId(), defaultEdition.getId());
        } else {
            projectVersionService.autoSaveVersion(project.getId());
        }
        projectActivityService.recordForCurrentUser(
                project.getId(),
                ProjectActivity.ACTION_PROJECT_CREATED,
                "imported the project from a file",
                ProjectActivity.ENTITY_PROJECT,
                project.getId());
        return project;
    }

    private static ScriptEdition resolveEdition(
            Integer editionKey, Map<Integer, ScriptEdition> editionsByKey, ScriptEdition fallback) {
        if (editionKey != null && editionsByKey.containsKey(editionKey)) {
            return editionsByKey.get(editionKey);
        }
        return fallback;
    }

    private static String clean(String value, int maxLength) {
        return truncate(PlainTextSanitizer.sanitizeSingleLine(value), maxLength);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
