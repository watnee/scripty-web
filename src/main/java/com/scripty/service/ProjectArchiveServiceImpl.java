package com.scripty.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.ScriptEdition;
import com.scripty.dto.SongEdition;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    @Autowired
    private SongEditionService songEditionService;

    @Autowired
    private SongVersionService songVersionService;

    @Autowired
    private SongBlockService songBlockService;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId) {
        ProjectArchive archive = buildArchive(projectId);
        if (archive == null) {
            return null;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(archive);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize project " + projectId, e);
        }
    }

    private ProjectArchive buildArchive(Integer projectId) {
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

        for (TextDocument document : textDocumentRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(projectId)) {
            ProjectArchive.Document entry = new ProjectArchive.Document();
            entry.key = document.getId();
            entry.uid = document.getUid();
            entry.title = document.getTitle();
            entry.documentType = document.getDocumentType();
            entry.content = document.getContent();
            entry.sortOrder = document.getSortOrder();
            entry.archived = document.isArchived();
            archive.documents.add(entry);
        }

        for (Block block : blockRepository.findByProjectIdOrderByOrderAscIdAsc(projectId)) {
            ProjectArchive.BlockEntry entry = new ProjectArchive.BlockEntry();
            entry.order = block.getOrder();
            entry.type = block.getType();
            entry.content = block.getContent();
            entry.sceneDelimiter = block.isSceneDelimiter();
            entry.textAlign = block.getTextAlign();
            entry.font = block.getFont();
            entry.highlight = block.getHighlight();
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

        return archive;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProjectsBundle(List<Integer> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return null;
        }

        ProjectArchiveBundle bundle = new ProjectArchiveBundle();
        bundle.format = ProjectArchiveBundle.FORMAT;
        bundle.formatVersion = ProjectArchiveBundle.CURRENT_VERSION;
        bundle.exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        for (Integer projectId : projectIds) {
            if (projectId == null) {
                continue;
            }
            ProjectArchive archive = buildArchive(projectId);
            if (archive != null) {
                bundle.projects.add(archive);
            }
        }
        if (bundle.projects.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize project bundle", e);
        }
    }

    @Override
    @Transactional
    public List<Project> importProjects(MultipartFile file) throws ScriptImportException {
        JsonNode root = readRoot(file);

        String format = root.path("format").asText(null);
        if (ProjectArchiveBundle.FORMAT.equals(format)) {
            ProjectArchiveBundle bundle = convert(root, ProjectArchiveBundle.class);
            checkVersion(bundle.formatVersion, ProjectArchiveBundle.CURRENT_VERSION);
            List<Project> imported = new ArrayList<>();
            if (bundle.projects != null) {
                for (ProjectArchive archive : bundle.projects) {
                    if (archive == null) {
                        continue;
                    }
                    // A bundle vouches for its entries, so tolerate entries that
                    // omit the per-project format marker.
                    checkVersion(archive.formatVersion > 0 ? archive.formatVersion : 1,
                            ProjectArchive.CURRENT_VERSION);
                    imported.add(importArchive(archive));
                }
            }
            if (imported.isEmpty()) {
                throw new ScriptImportException("That project file doesn't contain any projects.");
            }
            return imported;
        }

        if (!ProjectArchive.FORMAT.equals(format)) {
            throw new ScriptImportException(BAD_FILE_MESSAGE);
        }
        ProjectArchive archive = convert(root, ProjectArchive.class);
        checkVersion(archive.formatVersion, ProjectArchive.CURRENT_VERSION);
        return List.of(importArchive(archive));
    }

    @Override
    @Transactional
    public Project replaceProject(Integer projectId, MultipartFile file) throws ScriptImportException {
        // The file is read and vetted before anything is touched, so a project
        // is never half-emptied on behalf of a file that turns out not to be a
        // Scripty one.
        ProjectArchive archive = readSingleArchive(file);
        Project project = projectId == null ? null : projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getDeletedAt() != null) {
            return null;
        }

        // Everything here now, kept: the version history is where the writer
        // goes to find a draft this replaced, and it has to be written before
        // the blocks it describes are gone.
        ScriptEdition edition = scriptEditionService.ensureDefaultEdition(projectId);
        if (edition != null) {
            projectVersionService.autoSaveVersion(projectId, edition.getId());
        } else {
            projectVersionService.autoSaveVersion(projectId);
        }

        LocalDateTime now = LocalDateTime.now();
        applyInfo(project, archive.project, now);
        project = projectRepository.save(project);

        // Only this edition's script. Another draft of the same screenplay is
        // not what the file describes, and wiping it would be a surprise nobody
        // asked for — which is also why the file's own editions are ignored.
        List<Block> existingBlocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAscIdAsc(projectId);
        blockRepository.deleteAll(existingBlocks);
        List<Person> existingPeople = edition != null
                ? personRepository.findByScriptEditionIdOrderByNameAsc(edition.getId())
                : personRepository.findByProjectIdOrderByNameAsc(projectId);
        personRepository.deleteAll(existingPeople);

        // The songs and notes already here, offered to the file by uid. Whatever
        // it claims is written where it stands — same id, same lyric lines, same
        // version history — because a file coming back into the project it was
        // exported from is describing these documents, not replacements for
        // them. That is the whole of what makes a song survive a writer signing
        // out and back in as the same song.
        Map<String, TextDocument> existingByUid = new LinkedHashMap<>();
        for (TextDocument document
                : textDocumentRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(projectId)) {
            existingByUid.put(document.getUid(), document);
        }

        fillContents(project, archive, new HashMap<>(), edition, now, existingByUid);

        // What the file did not claim. Songs and notes go to the trash rather
        // than out of the database: a song has lyric lines, versions and
        // editions hanging off it, and the trash is where this app has always
        // put a document whose absence might turn out to be a mistake. A file
        // written before uids existed claims nothing, so everything lands here
        // and the replace behaves exactly as it did before this — whole, and
        // recoverable.
        for (TextDocument document : existingByUid.values()) {
            document.setDeletedAt(now);
            textDocumentRepository.save(document);
        }

        projectActivityService.recordForCurrentUser(
                projectId,
                ProjectActivity.ACTION_SCRIPT_IMPORTED,
                "replaced the project from a file",
                ProjectActivity.ENTITY_PROJECT,
                projectId);
        return project;
    }

    /** The one project a replace is about: a single-project file, or a bundle's first. */
    private ProjectArchive readSingleArchive(MultipartFile file) throws ScriptImportException {
        JsonNode root = readRoot(file);
        String format = root.path("format").asText(null);
        if (ProjectArchiveBundle.FORMAT.equals(format)) {
            ProjectArchiveBundle bundle = convert(root, ProjectArchiveBundle.class);
            checkVersion(bundle.formatVersion, ProjectArchiveBundle.CURRENT_VERSION);
            ProjectArchive first = bundle.projects == null
                    ? null
                    : bundle.projects.stream().filter(a -> a != null).findFirst().orElse(null);
            if (first == null) {
                throw new ScriptImportException("That project file doesn't contain any projects.");
            }
            // A bundle vouches for its entries, exactly as importing one does.
            checkVersion(first.formatVersion > 0 ? first.formatVersion : 1, ProjectArchive.CURRENT_VERSION);
            return first;
        }
        if (!ProjectArchive.FORMAT.equals(format)) {
            throw new ScriptImportException(BAD_FILE_MESSAGE);
        }
        ProjectArchive archive = convert(root, ProjectArchive.class);
        checkVersion(archive.formatVersion, ProjectArchive.CURRENT_VERSION);
        return archive;
    }

    private JsonNode readRoot(MultipartFile file) throws ScriptImportException {
        if (file == null || file.isEmpty()) {
            throw new ScriptImportException("No file selected. Choose a .scripty.json file exported from Scripty.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(file.getBytes());
        } catch (IOException e) {
            throw new ScriptImportException(BAD_FILE_MESSAGE, e);
        }
        if (root == null || !root.isObject()) {
            throw new ScriptImportException(BAD_FILE_MESSAGE);
        }
        return root;
    }

    private <T> T convert(JsonNode root, Class<T> type) throws ScriptImportException {
        try {
            return objectMapper.treeToValue(root, type);
        } catch (JsonProcessingException e) {
            throw new ScriptImportException(BAD_FILE_MESSAGE, e);
        }
    }

    private static void checkVersion(int formatVersion, int currentVersion) throws ScriptImportException {
        if (formatVersion < 1) {
            throw new ScriptImportException(BAD_FILE_MESSAGE);
        }
        if (formatVersion > currentVersion) {
            throw new ScriptImportException(
                    "That project file was exported by a newer version of Scripty and can't be imported here.");
        }
    }

    private Project importArchive(ProjectArchive archive) {
        LocalDateTime now = LocalDateTime.now();
        Project project = new Project();
        applyInfo(project, archive.project, now);
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

        // Nothing to claim: this project was made a moment ago. The file's uids
        // are still honoured, though, which is what makes the *first* crossing
        // work — a song kept from a signed-out device arrives in the account
        // under the name the device knows it by, so the next crossing can find
        // it again.
        fillContents(project, archive, editionsByKey, defaultEdition, now, new LinkedHashMap<>());

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

    /**
     * Writes a song's lyric to match the text a file brought, keeping what it
     * said first.
     *
     * The snapshot is the point. A replace happens when the client believes the
     * account's copy has not moved since the two were last in step, and that
     * belief is deliberately coarse — the project's own edit date does not move
     * for a song. So the case this guards is real: someone wrote a verse in a
     * browser, the device could not know, and the words are one restore away
     * rather than gone.
     */
    private void replaceLyric(TextDocument document, String content) {
        SongEdition edition = songEditionService.ensureDefaultEdition(document.getId());
        if (edition != null) {
            songVersionService.autoSaveVersion(document.getId(), edition.getId());
        }
        songBlockService.replaceLinesFromContent(document.getId(), content);
    }

    /** The title page, common to importing a file and replacing a project with one. */
    private void applyInfo(Project project, ProjectArchive.Info source, LocalDateTime now) {
        ProjectArchive.Info info = source != null ? source : new ProjectArchive.Info();
        String title = clean(info.title, 100);
        project.setTitle(title != null && !title.isBlank() ? title : "Imported Project");
        project.setScreenplayTitle(clean(info.screenplayTitle, 255));
        project.setWriters(clean(info.writers, 255));
        project.setContactInfo(truncate(PlainTextSanitizer.sanitize(info.contactInfo), 1000));
        project.setScreenplayVersion(clean(info.screenplayVersion, 255));
        project.setLastEdited(now);
    }

    /**
     * Everything the file says a project contains — its songs and notes, its
     * characters and its script — written into a project that is ready for
     * them. Shared by importing a file into a new project and reading one back
     * into a project that already exists; the difference between those two is
     * what happened before this ran, not what it does.
     */
    private void fillContents(Project project, ProjectArchive archive,
                              Map<Integer, ScriptEdition> editionsByKey,
                              ScriptEdition defaultEdition, LocalDateTime now,
                              Map<String, TextDocument> claimable) {
        Map<Integer, TextDocument> documentsByKey = new HashMap<>();
        // Every uid this project will answer to once the loop is done: the ones
        // still waiting to be claimed, plus the ones already written. A second
        // entry naming a uid one of those holds is not that document — it is a
        // file describing the same song twice — and must not be given its name.
        Set<String> spokenFor = new HashSet<>(claimable.keySet());
        int documentSequence = 0;
        if (archive.documents != null) {
            for (ProjectArchive.Document entry : archive.documents) {
                if (entry == null) {
                    continue;
                }
                // A file that names a song already in this project is talking
                // about that song. Claiming it takes it off the list of things
                // this replace is about to trash, and everything below writes
                // into the document that is already there.
                String uid = clean(entry.uid, 64);
                if (uid != null && uid.isBlank()) {
                    uid = null;
                }
                TextDocument document = uid != null ? claimable.remove(uid) : null;
                boolean isNew = document == null;
                if (isNew) {
                    document = new TextDocument();
                    document.setProject(project);
                    document.setCreatedAt(now);
                    // Keep the file's name for it where nothing here answers to
                    // that name yet — that is how a song written on a signed-out
                    // device goes on being the same song once it is in an
                    // account. A name already spoken for belongs to a document
                    // this entry is not describing, so that one starts afresh
                    // (assignUid mints it).
                    document.setUid(uid != null && spokenFor.add(uid) ? uid : null);
                }
                String docTitle = clean(entry.title, 200);
                document.setTitle(docTitle != null && !docTitle.isBlank() ? docTitle : "Untitled");
                String docType = entry.documentType != null ? entry.documentType.trim().toUpperCase() : null;
                document.setDocumentType(
                        docType != null && TextDocument.DOCUMENT_TYPES.contains(docType)
                                ? docType
                                : TextDocument.TYPE_OTHER);
                String content = PlainTextSanitizer.sanitize(entry.content);
                boolean lyricChanged = !isNew
                        && TextDocument.TYPE_SONG.equals(document.getDocumentType())
                        && !Objects.equals(content, document.getContent());
                document.setContent(content);
                document.setSortOrder(entry.sortOrder != null ? entry.sortOrder : documentSequence);
                // A song put aside stays put aside through the round trip. Files
                // written before the flag existed carry false, which is what
                // they all meant.
                document.setArchivedAt(entry.archived ? now : null);
                document.setUpdatedAt(now);
                document = textDocumentRepository.save(document);
                // A new song's lines are seeded from this text the first time
                // anything asks for them. One that already exists has lines
                // already, and seeding skips a song that has any — so its lyric
                // would go on showing what it said before the file arrived,
                // while the text underneath said something else. Rewrite them,
                // keeping what they said in the song's own version history.
                if (lyricChanged) {
                    replaceLyric(document, content);
                }
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
                    String font = entry.font != null ? entry.font.trim().toUpperCase() : null;
                    block.setFont(font != null && Block.FONTS.contains(font) ? font : null);
                    block.setHighlight(Block.normalizeHighlight(entry.highlight));
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
