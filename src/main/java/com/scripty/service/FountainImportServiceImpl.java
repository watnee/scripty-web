package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.dto.ProjectActivity;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.util.PlainTextSanitizer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FountainImportServiceImpl implements FountainImportService {

    private static final Pattern SCENE_HEADING = Pattern.compile(
            "^(?:INT\\.?|EXT\\.?|EST\\.?|INT\\.?/EXT\\.?|I/E\\.?)\\s+.+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSITION = Pattern.compile("^[A-Z][A-Z0-9 ]+ TO:$");
    private static final Pattern SHOT = Pattern.compile(
            "^(?:ANGLE ON|ANOTHER ANGLE|CLOSE ON|CLOSE UP|CLOSEUP|C\\.U\\.?|CU|POV|INSERT|"
                    + "BACK TO SCENE|BACK TO|TIGHT ON|WIDER(?: SHOT)?|TRACKING|CRANE|"
                    + "AERIAL|ESTABLISHING|FAVOR ON|REVERSE ANGLE)\\b.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PAGE_KEY = Pattern.compile(
            "^(Title|Credit|Author|Authors|Writer|Writers|Source|Draft date|Draft|Version|Contact|Contact Info|Contact Information)\\s*:(.*)$",
            Pattern.CASE_INSENSITIVE);

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectVersionService projectVersionService;

    @Autowired
    private ScriptImportTextExtractor scriptImportTextExtractor;

    @Autowired
    private ProjectActivityService projectActivityService;

    @Autowired
    private ScriptEditionService scriptEditionService;

    private enum ParseMode {
        ACTION, CHARACTER, DIALOGUE
    }

    private record ParsedBlock(String type, String content, String characterName) {
        ParsedBlock(String type, String content) {
            this(type, content, null);
        }
    }

    @Override
    @Transactional
    public void importFileIntoProject(Integer projectId, MultipartFile file) throws IOException {
        ImportOutcome outcome = importFileIntoProjectWithStatus(projectId, file);
        if (!outcome.success()) {
            throw new ScriptImportException(outcome.message());
        }
    }

    @Override
    @Transactional
    public ImportOutcome importFileIntoProjectWithStatus(Integer projectId, MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            return ImportOutcome.fail(
                    "No file selected. Choose a .fountain, .txt, .docx, .doc, .fdx, or .pdf file.");
        }
        try {
            ScriptImportTextExtractor.Extraction extraction =
                    scriptImportTextExtractor.extractWithMeta(file);
            if (extraction.isBlank()) {
                if (extraction.wasPdf()) {
                    return ImportOutcome.fail(
                            "No text found in that PDF. Scanned or image-only PDFs aren’t supported — use a text-based PDF, Fountain, or Final Draft file.");
                }
                return ImportOutcome.fail(
                        "That file was empty. Try a .fountain, .txt, .docx, .doc, .fdx, or .pdf file.");
            }
            importIntoProject(projectId, extraction.text());
            if (extraction.wasPdf() && !extraction.pdfUsedScreenplayLayout()) {
                return ImportOutcome.ok(
                        "Imported as plain text; element types may need cleanup. Best results come from Scripty-exported or standard screenplay-layout PDFs.");
            }
            return ImportOutcome.ok("Script imported.");
        } catch (ScriptImportException e) {
            return ImportOutcome.fail(e.getUserMessage());
        } catch (IOException e) {
            return ImportOutcome.fail(
                    "Could not import that file. Check access and try a .fountain, .txt, .docx, .doc, .fdx, or .pdf file.");
        }
    }

    @Override
    @Transactional
    public void importIntoProject(Integer projectId, String fountainText) {
        if (fountainText == null || fountainText.isBlank()) {
            return;
        }

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }

        TitlePageParseResult titlePage = extractTitlePage(fountainText);
        if (titlePage.hasAny()) {
            if (titlePage.title() != null) {
                project.setScreenplayTitle(PlainTextSanitizer.sanitizeSingleLine(titlePage.title()));
            }
            if (titlePage.writers() != null) {
                project.setWriters(PlainTextSanitizer.sanitizeSingleLine(titlePage.writers()));
            }
            if (titlePage.contact() != null) {
                project.setContactInfo(PlainTextSanitizer.sanitize(titlePage.contact()));
            }
            if (titlePage.version() != null) {
                project.setScreenplayVersion(PlainTextSanitizer.sanitizeSingleLine(titlePage.version()));
            }
        }

        List<ParsedBlock> parsed = parse(titlePage.body());
        if (parsed.isEmpty() && !titlePage.hasAny()) {
            return;
        }

        ScriptEdition edition = scriptEditionService.ensureDefaultEdition(projectId);
        List<Block> existing = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAsc(projectId);
        if (!existing.isEmpty()) {
            blockRepository.deleteAll(existing);
        }

        Map<String, Person> characterCache = new HashMap<>();
        int order = 1;
        for (ParsedBlock parsedBlock : parsed) {
            Block block = new Block();
            block.setProject(project);
            block.setScriptEdition(edition);
            block.setOrder(order++);
            block.setContent(PlainTextSanitizer.sanitize(parsedBlock.content()));
            block.setType(parsedBlock.type());
            block.setBookmarked(false);
            block.setPinned(false);
            block.setSceneDelimiter(false);

            if ((Block.isCharacterCueType(parsedBlock.type()) || Block.TYPE_DIALOGUE.equals(parsedBlock.type()))
                    && parsedBlock.characterName() != null) {
                block.setPerson(findOrCreatePerson(project, parsedBlock.characterName(), characterCache));
            }

            blockRepository.save(block);
        }

        project.setLastEdited(LocalDateTime.now());
        projectRepository.save(project);
        projectVersionService.autoSaveVersion(projectId);
        projectActivityService.recordForCurrentUser(
                projectId,
                ProjectActivity.ACTION_SCRIPT_IMPORTED,
                "imported a script",
                ProjectActivity.ENTITY_PROJECT,
                projectId);
    }

    /**
     * Fountain title pages are key/value lines before the first blank line that
     * separates metadata from the script body. Supports Title, Credit, Author(s),
     * Contact, Draft date / Version, and related keys.
     */
    private static TitlePageParseResult extractTitlePage(String fountainText) {
        String normalized = fountainText.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);

        boolean looksLikeTitlePage = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                break;
            }
            if (TITLE_PAGE_KEY.matcher(trimmed).matches()) {
                looksLikeTitlePage = true;
                break;
            }
            // First non-empty line isn't a title-page key → no title page
            break;
        }

        if (!looksLikeTitlePage) {
            return new TitlePageParseResult(null, null, null, null, normalized);
        }

        String title = null;
        String writers = null;
        String contact = null;
        String version = null;
        StringBuilder contactBuf = new StringBuilder();
        boolean inContact = false;
        int bodyStart = 0;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.trim();

            if (trimmed.isEmpty()) {
                // End of title page: first blank line after keys
                bodyStart = i + 1;
                // Skip additional blank lines before body
                while (bodyStart < lines.length && lines[bodyStart].trim().isEmpty()) {
                    bodyStart++;
                }
                break;
            }

            var matcher = TITLE_PAGE_KEY.matcher(trimmed);
            if (matcher.matches()) {
                inContact = false;
                String key = matcher.group(1).toLowerCase(Locale.ROOT);
                String value = matcher.group(2) != null ? matcher.group(2).trim() : "";
                switch (key) {
                    case "title" -> title = value.isEmpty() ? title : value;
                    case "author", "authors", "writers", "writer" ->
                            writers = value.isEmpty() ? writers : value;
                    case "credit" -> {
                        // Keep credit with writers when present: "Written by\nJane Doe"
                        if (!value.isEmpty() && writers == null) {
                            writers = value;
                        } else if (!value.isEmpty()) {
                            writers = value + (writers != null && !writers.isBlank() ? "\n" + writers : "");
                        }
                    }
                    case "draft date", "draft", "version" ->
                            version = value.isEmpty() ? version : value;
                    case "contact", "contact info", "contact information" -> {
                        inContact = true;
                        if (!value.isEmpty()) {
                            contactBuf.append(value);
                        }
                    }
                    default -> {
                        // Ignore Source, etc.
                    }
                }
            } else if (inContact) {
                if (contactBuf.length() > 0) {
                    contactBuf.append('\n');
                }
                contactBuf.append(trimmed);
            } else if (writers != null && !TITLE_PAGE_KEY.matcher(trimmed).matches()) {
                // Multi-line author continuation
                writers = writers + "\n" + trimmed;
            }
            bodyStart = i + 1;
        }

        if (contactBuf.length() > 0) {
            contact = contactBuf.toString().trim();
        }

        StringBuilder body = new StringBuilder();
        for (int i = bodyStart; i < lines.length; i++) {
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(lines[i]);
        }

        return new TitlePageParseResult(title, writers, contact, version, body.toString());
    }

    private record TitlePageParseResult(String title, String writers, String contact, String version, String body) {
        boolean hasAny() {
            return (title != null && !title.isBlank())
                    || (writers != null && !writers.isBlank())
                    || (contact != null && !contact.isBlank())
                    || (version != null && !version.isBlank());
        }
    }

    List<ParsedBlock> parse(String fountainText) {
        List<ParsedBlock> blocks = new ArrayList<>();
        String[] lines = fountainText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        ParseMode mode = ParseMode.ACTION;
        String pendingCharacter = null;
        StringBuilder dialogueBuffer = new StringBuilder();
        boolean inBoneyard = false;

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();

            if (inBoneyard) {
                if (trimmed.contains("*/")) {
                    inBoneyard = false;
                }
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBoneyard = true;
                }
                continue;
            }

            if (trimmed.startsWith("[[") && trimmed.endsWith("]]")) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_NOTE, trimmed.substring(2, trimmed.length() - 2).trim()));
                continue;
            }

            if (trimmed.isEmpty()) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                continue;
            }

            if (trimmed.matches("^={3,}$")) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_PAGE_BREAK, "==="));
                continue;
            }

            if (trimmed.startsWith("#")) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_SECTION, trimmed.replaceFirst("^#+", "").trim()));
                continue;
            }

            if (trimmed.startsWith("=") && !trimmed.startsWith("==")) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_SYNOPSIS, trimmed.substring(1).trim()));
                continue;
            }

            if (trimmed.startsWith("~")) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_LYRICS, trimmed.substring(1).trim()));
                continue;
            }

            if (trimmed.startsWith(".") && !trimmed.startsWith("..")) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_SCENE, trimmed.substring(1).trim()));
                continue;
            }

            if (SCENE_HEADING.matcher(trimmed).matches()) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_SCENE, trimmed));
                continue;
            }

            if (trimmed.startsWith(">") && trimmed.endsWith("<") && trimmed.length() > 2) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_CENTERED, trimmed.substring(1, trimmed.length() - 1).trim()));
                continue;
            }

            if (trimmed.startsWith(">")) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_TRANSITION, trimmed.substring(1).trim()));
                continue;
            }

            if (TRANSITION.matcher(trimmed).matches()) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_TRANSITION, trimmed));
                continue;
            }

            if (SHOT.matcher(trimmed).matches()) {
                flushDialogue(blocks, dialogueBuffer);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_SHOT, trimmed));
                continue;
            }

            if ((mode == ParseMode.CHARACTER || mode == ParseMode.DIALOGUE) && trimmed.startsWith("(")) {
                flushDialogue(blocks, dialogueBuffer);
                String parenContent = trimmed.endsWith(")")
                        ? trimmed.substring(1, trimmed.length() - 1).trim()
                        : trimmed.substring(1).trim();
                blocks.add(new ParsedBlock(Block.TYPE_PARENTHETICAL, parenContent));
                mode = ParseMode.DIALOGUE;
                continue;
            }

            if (mode == ParseMode.ACTION && isCharacterLine(trimmed)) {
                flushDialogue(blocks, dialogueBuffer);
                pendingCharacter = normalizeCharacterName(trimmed);
                String cueType = isDualDialogueCue(trimmed) ? Block.TYPE_DUAL_DIALOGUE : Block.TYPE_CHARACTER;
                blocks.add(new ParsedBlock(cueType, pendingCharacter, pendingCharacter));
                mode = ParseMode.CHARACTER;
                continue;
            }

            if (mode == ParseMode.CHARACTER || mode == ParseMode.DIALOGUE) {
                if (dialogueBuffer.length() > 0) {
                    dialogueBuffer.append('\n');
                }
                dialogueBuffer.append(rawLine.stripTrailing());
                mode = ParseMode.DIALOGUE;
                continue;
            }

            flushDialogue(blocks, dialogueBuffer);
            pendingCharacter = null;
            if (trimmed.startsWith("!")) {
                blocks.add(new ParsedBlock(Block.TYPE_ACTION, trimmed.substring(1)));
            } else {
                blocks.add(new ParsedBlock(Block.TYPE_ACTION, trimmed));
            }
            mode = ParseMode.ACTION;
        }

        flushDialogue(blocks, dialogueBuffer);

        return blocks;
    }

    private static void flushDialogue(List<ParsedBlock> blocks, StringBuilder dialogueBuffer) {
        if (dialogueBuffer.length() > 0) {
            // Character cue is already a separate CHARACTER block; dialogue content stands alone.
            blocks.add(new ParsedBlock(Block.TYPE_DIALOGUE, dialogueBuffer.toString().trim()));
            dialogueBuffer.setLength(0);
        }
    }

    private static boolean isCharacterLine(String line) {
        if (line.isEmpty() || line.length() > 60) {
            return false;
        }
        if (SCENE_HEADING.matcher(line).matches() || TRANSITION.matcher(line).matches()
                || SHOT.matcher(line).matches()) {
            return false;
        }
        String withoutModifiers = line.replaceAll("\\^(\\*)?", "").trim();
        if (withoutModifiers.startsWith("@")) {
            withoutModifiers = withoutModifiers.substring(1).trim();
        }
        if (withoutModifiers.isEmpty()) {
            return false;
        }
        String lettersOnly = withoutModifiers.replaceAll("[^A-Za-z]", "");
        if (lettersOnly.isEmpty()) {
            return false;
        }
        return withoutModifiers.equals(withoutModifiers.toUpperCase(Locale.ROOT));
    }

    private static String normalizeCharacterName(String line) {
        String name = line.replaceAll("\\^(\\*)?", "").trim();
        if (name.startsWith("@")) {
            name = name.substring(1).trim();
        }
        return name;
    }

    private static boolean isDualDialogueCue(String line) {
        return line != null && line.trim().endsWith("^");
    }

    private Person findOrCreatePerson(Project project, String name, Map<String, Person> cache) {
        String key = name.toUpperCase(Locale.ROOT);
        Person cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        ScriptEdition edition = scriptEditionService.getDefaultForProject(project.getId());
        List<Person> persons = edition != null
                ? personRepository.findByScriptEditionIdOrderByNameAsc(edition.getId())
                : personRepository.findByProjectIdOrderByNameAsc(project.getId());
        for (Person person : persons) {
            if (person.getName() != null && person.getName().equalsIgnoreCase(name)) {
                cache.put(key, person);
                return person;
            }
        }

        Person person = new Person();
        person.setName(PlainTextSanitizer.sanitizeSingleLine(name));
        person.setFullName(PlainTextSanitizer.sanitizeSingleLine(name));
        person.setProject(project);
        person.setScriptEdition(edition != null ? edition : scriptEditionService.ensureDefaultEdition(project.getId()));
        person = personRepository.save(person);
        cache.put(key, person);
        return person;
    }
}
