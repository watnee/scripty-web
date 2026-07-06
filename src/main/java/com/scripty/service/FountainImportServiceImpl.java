package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
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
        importIntoProject(projectId, scriptImportTextExtractor.extract(file));
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

        List<ParsedBlock> parsed = parse(fountainText);
        if (parsed.isEmpty()) {
            return;
        }

        List<Block> existing = blockRepository.findByProjectIdOrderByOrderAsc(projectId);
        if (!existing.isEmpty()) {
            blockRepository.deleteAll(existing);
        }

        Map<String, Person> characterCache = new HashMap<>();
        int order = 1;
        for (ParsedBlock parsedBlock : parsed) {
            Block block = new Block();
            block.setProject(project);
            block.setOrder(order++);
            block.setContent(parsedBlock.content());
            block.setType(parsedBlock.type());
            block.setBookmarked(false);
            block.setPinned(false);

            if (Block.TYPE_DIALOGUE.equals(parsedBlock.type()) && parsedBlock.characterName() != null) {
                block.setPerson(findOrCreatePerson(project, parsedBlock.characterName(), characterCache));
            }

            blockRepository.save(block);
        }

        project.setLastEdited(LocalDateTime.now());
        projectRepository.save(project);
        projectVersionService.autoSaveVersion(projectId);
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
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_NOTE, trimmed.substring(2, trimmed.length() - 2).trim()));
                continue;
            }

            if (trimmed.isEmpty()) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                continue;
            }

            if (trimmed.matches("^={3,}$")) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_PAGE_BREAK, "==="));
                continue;
            }

            if (trimmed.startsWith("#")) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_SECTION, trimmed.replaceFirst("^#+", "").trim()));
                continue;
            }

            if (trimmed.startsWith("=") && !trimmed.startsWith("==")) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_SYNOPSIS, trimmed.substring(1).trim()));
                continue;
            }

            if (trimmed.startsWith("~")) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_LYRICS, trimmed.substring(1).trim()));
                continue;
            }

            if (trimmed.startsWith(".") && !trimmed.startsWith("..")) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_SCENE, trimmed.substring(1).trim()));
                continue;
            }

            if (SCENE_HEADING.matcher(trimmed).matches()) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_SCENE, trimmed));
                continue;
            }

            if (trimmed.startsWith(">") && trimmed.endsWith("<") && trimmed.length() > 2) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_CENTERED, trimmed.substring(1, trimmed.length() - 1).trim()));
                continue;
            }

            if (trimmed.startsWith(">")) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_TRANSITION, trimmed.substring(1).trim()));
                continue;
            }

            if (TRANSITION.matcher(trimmed).matches()) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                mode = ParseMode.ACTION;
                pendingCharacter = null;
                blocks.add(new ParsedBlock(Block.TYPE_TRANSITION, trimmed));
                continue;
            }

            if ((mode == ParseMode.CHARACTER || mode == ParseMode.DIALOGUE) && trimmed.startsWith("(")) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                String parenContent = trimmed.endsWith(")")
                        ? trimmed.substring(1, trimmed.length() - 1).trim()
                        : trimmed.substring(1).trim();
                blocks.add(new ParsedBlock(Block.TYPE_PARENTHETICAL, parenContent));
                mode = ParseMode.DIALOGUE;
                continue;
            }

            if (mode == ParseMode.ACTION && isCharacterLine(trimmed)) {
                flushDialogue(blocks, dialogueBuffer, pendingCharacter);
                pendingCharacter = normalizeCharacterName(trimmed);
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

            flushDialogue(blocks, dialogueBuffer, pendingCharacter);
            pendingCharacter = null;
            blocks.add(new ParsedBlock(Block.TYPE_ACTION, trimmed));
            mode = ParseMode.ACTION;
        }

        flushDialogue(blocks, dialogueBuffer, pendingCharacter);

        if (blocks.stream().noneMatch(block -> Block.TYPE_SCENE.equals(block.type()))) {
            blocks.add(0, new ParsedBlock(Block.TYPE_SCENE, " "));
        }

        return blocks;
    }

    private static void flushDialogue(List<ParsedBlock> blocks, StringBuilder dialogueBuffer, String characterName) {
        if (dialogueBuffer.length() > 0) {
            blocks.add(new ParsedBlock(Block.TYPE_DIALOGUE, dialogueBuffer.toString().trim(), characterName));
            dialogueBuffer.setLength(0);
        }
    }

    private static boolean isCharacterLine(String line) {
        if (line.isEmpty() || line.length() > 60) {
            return false;
        }
        if (SCENE_HEADING.matcher(line).matches() || TRANSITION.matcher(line).matches()) {
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

    private Person findOrCreatePerson(Project project, String name, Map<String, Person> cache) {
        String key = name.toUpperCase(Locale.ROOT);
        Person cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        for (Person person : personRepository.findByProjectIdOrderByNameAsc(project.getId())) {
            if (person.getName() != null && person.getName().equalsIgnoreCase(name)) {
                cache.put(key, person);
                return person;
            }
        }

        Person person = new Person();
        person.setName(name);
        person.setFullName(name);
        person.setProject(project);
        person = personRepository.save(person);
        cache.put(key, person);
        return person;
    }
}
