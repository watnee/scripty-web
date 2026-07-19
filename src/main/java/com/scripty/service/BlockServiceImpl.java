package com.scripty.service;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.DeletedBlock;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.User;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.DeletedBlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.viewmodel.block.BlockViewModel;
import com.scripty.viewmodel.block.createblock.CreateBlockViewModel;
import com.scripty.viewmodel.block.createblock.CreatePersonViewModel;
import com.scripty.viewmodel.block.createblockbelow.CreateBlockBelowViewModel;
import com.scripty.util.BlockFormatting;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.block.editblock.EditBlockViewModel;
import com.scripty.viewmodel.block.editblock.EditPersonViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockServiceImpl implements BlockService {

    private final BlockRepository blockRepository;
    private final DeletedBlockRepository deletedBlockRepository;
    private final PersonRepository personRepository;
    private final ProjectRepository projectRepository;
    private final ProjectActivityService projectActivityService;
    private final ProjectUndoRedoService projectUndoRedoService;
    private final ScriptEditionService scriptEditionService;
    private final UserService userService;

    @Autowired
    public BlockServiceImpl(BlockRepository blockRepository,
                            DeletedBlockRepository deletedBlockRepository,
                            PersonRepository personRepository,
                            ProjectRepository projectRepository,
                            ProjectActivityService projectActivityService,
                            @org.springframework.context.annotation.Lazy ProjectUndoRedoService projectUndoRedoService,
                            ScriptEditionService scriptEditionService,
                            UserService userService) {
        this.blockRepository = blockRepository;
        this.deletedBlockRepository = deletedBlockRepository;
        this.personRepository = personRepository;
        this.projectRepository = projectRepository;
        this.projectActivityService = projectActivityService;
        this.projectUndoRedoService = projectUndoRedoService;
        this.scriptEditionService = scriptEditionService;
        this.userService = userService;
    }

    @Override
    public Block read(Integer id) {
        return blockRepository.findById(id).orElse(null);
    }

    @Override
    public CreateBlockViewModel getCreateBlockViewModel(Integer projectId) {
        CreateBlockViewModel vm = new CreateBlockViewModel();
        Project project = projectRepository.findById(projectId).orElse(null);

        CreateBlockCommandModel commandModel = new CreateBlockCommandModel();
        commandModel.setProjectId(project.getId());
        vm.setCreateBlockCommandModel(commandModel);

        ScriptEdition edition = resolveEdition(project);
        List<Person> persons = edition != null
                ? personRepository.findByScriptEditionIdOrderByNameAsc(edition.getId())
                : personRepository.findByProjectIdOrderByNameAsc(project.getId());
        vm.setProjectId(project.getId());
        vm.setPersons(translateCreatePersonViewModel(persons));
        return vm;
    }

    @Override
    public CreateBlockBelowViewModel getCreateBlockBelowViewModel(Integer id) {
        CreateBlockBelowViewModel vm = new CreateBlockBelowViewModel();
        Block existingBlock = blockRepository.findById(id).orElse(null);
        Project project = existingBlock.getProject();

        CreateBlockBelowCommandModel commandModel = new CreateBlockBelowCommandModel();
        commandModel.setId(existingBlock.getId());
        vm.setCreateBlockBelowCommandModel(commandModel);

        ScriptEdition edition = resolveEdition(existingBlock);
        List<Person> persons = edition != null
                ? personRepository.findByScriptEditionIdOrderByNameAsc(edition.getId())
                : personRepository.findByProjectIdOrderByNameAsc(project.getId());
        vm.setProjectId(project.getId());
        vm.setPersons(translateCreatePersonViewModel(persons));
        return vm;
    }

    @Override
    public EditBlockViewModel getEditBlockViewModel(Integer id) {
        EditBlockViewModel vm = new EditBlockViewModel();
        Block existingBlock = blockRepository.findById(id).orElse(null);
        Project project = existingBlock.getProject();

        vm.setProjectId(project.getId());

        ScriptEdition edition = resolveEdition(existingBlock);
        List<Person> allPersons = edition != null
                ? personRepository.findByScriptEditionIdOrderByNameAsc(edition.getId())
                : personRepository.findByProjectIdOrderByNameAsc(project.getId());
        List<EditPersonViewModel> editPersonViewModels = new ArrayList<>();
        for (Person person : allPersons) {
            EditPersonViewModel epvm = new EditPersonViewModel();
            epvm.setId(person.getId());
            epvm.setName(person.getName());
            editPersonViewModels.add(epvm);
        }
        vm.setPersons(editPersonViewModels);

        EditBlockCommandModel commandModel = new EditBlockCommandModel();
        commandModel.setId(existingBlock.getId());
        commandModel.setContent(existingBlock.getContent());
        if (existingBlock.getPerson() != null) {
            commandModel.setPersonId(existingBlock.getPerson().getId());
        }
        commandModel.setTags(existingBlock.getTags());
        vm.setEditBlockCommandModel(commandModel);
        return vm;
    }

    @Override
    public BlockViewModel getBlockViewModel(Integer id) {
        Block block = blockRepository.findById(id).orElse(null);
        BlockViewModel vm = new BlockViewModel();
        vm.setId(block.getId());
        vm.setOrder(block.getOrder());
        vm.setContent(block.getContent());
        vm.setBookmarked(block.isBookmarked());
        vm.setPinned(block.isPinned());
        vm.setScene(block.isScene());
        vm.setType(block.getType());
        vm.setTags(block.getTags());
        vm.setTextAlign(block.getTextAlign());
        vm.setFont(block.getFont());
        vm.setHighlight(block.getHighlight());
        vm.setTextBold(block.isTextBold());
        vm.setTextItalic(block.isTextItalic());
        vm.setTextUnderline(block.isTextUnderline());
        if (block.getPerson() != null) {
            Person person = personRepository.findById(block.getPerson().getId()).orElse(null);
            if (person != null) {
                vm.setPersonId(person.getId());
                vm.setPersonName(person.getName());
            }
        }
        return vm;
    }

    @Override
    @Transactional
    public Block saveCreateBlockCommandModel(CreateBlockCommandModel cmd) {
        Block block = new Block();
        Person person = null;
        if (cmd.getPersonId() != null) {
            person = personRepository.findById(cmd.getPersonId()).orElse(null);
        }
        Project project = projectRepository.findById(cmd.getProjectId()).orElse(null);
        ScriptEdition edition = resolveEdition(project);
        String content = PlainTextSanitizer.sanitize(cmd.getContent());

        if (person == null) {
            String characterName = extractCharacterName(content);
            if (characterName != null) {
                person = findOrCreatePerson(characterName, project, edition);
                content = stripCharacterName(content);
            }
        }

        block.setContent(content);
        if (person != null) block.setPerson(person);
        block.setProject(project);
        block.setScriptEdition(edition);
        block.setBookmarked(false);
        block.setPinned(false);
        block.setType(normalizeBlockType(cmd.getType()));
        if (Block.isCharacterCueType(block.getType())) {
            String characterName = normalizeCharacterCue(content);
            if (characterName != null) {
                block.setContent(characterName);
                block.setPerson(findOrCreatePerson(characterName, project, edition));
            }
        }

        // Append after the highest existing order. Counting rows instead would
        // collide with a live block whenever orders are not a dense 1..N run.
        Integer maxOrder = edition != null
                ? blockRepository.findMaxOrderByScriptEditionId(edition.getId())
                : blockRepository.findMaxOrderByProjectId(project.getId());
        block.setOrder((maxOrder != null ? maxOrder : 0) + 1);
        Block saved = blockRepository.save(block);
        recordScriptEdited(project, edition);
        return saved;
    }

    @Override
    @Transactional
    public Block saveCreateBlockBelowCommandModel(CreateBlockBelowCommandModel cmd) {
        Block existingBlock = blockRepository.findById(cmd.getId()).orElse(null);
        Person person = null;
        if (cmd.getPersonId() != null) {
            person = personRepository.findById(cmd.getPersonId()).orElse(null);
        }
        Project project = existingBlock.getProject();
        ScriptEdition edition = resolveEdition(existingBlock);
        String content = PlainTextSanitizer.sanitize(cmd.getContent());
        if (content == null) {
            content = "";
        }

        if (person == null) {
            String characterName = extractCharacterName(content);
            if (characterName != null) {
                person = findOrCreatePerson(characterName, project, edition);
                content = stripCharacterName(content);
            }
        }

        // Always insert a new block below the anchor. Do not reuse a lone empty
        // block — the + menu and Enter-to-create expect a distinct new row.

        Block block = new Block();
        block.setContent(content);
        if (person != null) block.setPerson(person);
        block.setProject(project);
        block.setScriptEdition(edition);
        block.setBookmarked(false);
        block.setPinned(false);
        block.setType(normalizeBlockType(cmd.getType()));
        if (Block.isCharacterCueType(block.getType())) {
            String characterName = normalizeCharacterCue(content);
            if (characterName != null) {
                block.setContent(characterName);
                block.setPerson(findOrCreatePerson(characterName, project, edition));
            }
        }

        int newOrder = existingBlock.getOrder() + 1;
        if (edition == null) {
            edition = scriptEditionService.ensureDefaultEdition(project.getId());
            block.setScriptEdition(edition);
        }
        if (edition == null) {
            return existingBlock;
        }
        blockRepository.incrementOrdersAbove(existingBlock.getOrder(), edition.getId());
        block.setOrder(newOrder);
        Block saved = blockRepository.save(block);
        recordScriptEdited(project, edition);
        return saved;
    }

    @Override
    @Transactional
    public List<Block> insertBlocksAfter(Integer afterBlockId, List<CreateBlockBelowCommandModel> blocks) {
        List<Block> created = new ArrayList<>();
        if (blocks == null || blocks.isEmpty()) {
            return created;
        }

        Block afterBlock = afterBlockId != null ? blockRepository.findById(afterBlockId).orElse(null) : null;
        if (afterBlock == null) {
            return created;
        }

        Project project = afterBlock.getProject();
        if (project == null) {
            return created;
        }
        ScriptEdition edition = resolveEdition(afterBlock);
        if (edition == null) {
            return created;
        }

        // If the only block is empty, fill it with the first line then insert the rest below.
        String existingContent = afterBlock.getContent();
        boolean fillEmptyOnlyBlock = (existingContent == null || existingContent.trim().isEmpty())
                && blockRepository.countByScriptEditionId(edition.getId()) == 1;

        List<CreateBlockBelowCommandModel> remaining = blocks;
        if (fillEmptyOnlyBlock) {
            CreateBlockBelowCommandModel first = blocks.get(0);
            afterBlock.setContent(PlainTextSanitizer.sanitize(
                    first.getContent() != null ? first.getContent() : ""));
            afterBlock.setType(normalizeBlockType(first.getType()));
            afterBlock.setPerson(null);
            afterBlock.setSourceDocumentId(first.getSourceDocumentId());
            if (Block.isCharacterCueType(afterBlock.getType())) {
                String characterName = normalizeCharacterCue(afterBlock.getContent());
                if (characterName != null) {
                    afterBlock.setContent(characterName);
                    afterBlock.setPerson(findOrCreatePerson(characterName, project, edition));
                }
            }
            created.add(blockRepository.save(afterBlock));
            remaining = blocks.subList(1, blocks.size());
            if (remaining.isEmpty()) {
                recordScriptEdited(project, edition);
                return created;
            }
        }

        int insertCount = remaining.size();
        int baseOrder = afterBlock.getOrder();
        blockRepository.incrementOrdersAboveBy(baseOrder, edition.getId(), insertCount);

        int nextOrder = baseOrder + 1;
        for (CreateBlockBelowCommandModel cmd : remaining) {
            Block block = new Block();
            String content = PlainTextSanitizer.sanitize(cmd.getContent() != null ? cmd.getContent() : "");
            block.setContent(content);
            block.setProject(project);
            block.setScriptEdition(edition);
            block.setBookmarked(false);
            block.setPinned(false);
            block.setType(normalizeBlockType(cmd.getType()));
            block.setSourceDocumentId(cmd.getSourceDocumentId());
            if (Block.isCharacterCueType(block.getType())) {
                String characterName = normalizeCharacterCue(content);
                if (characterName != null) {
                    block.setContent(characterName);
                    block.setPerson(findOrCreatePerson(characterName, project, edition));
                }
            }
            block.setOrder(nextOrder++);
            created.add(blockRepository.save(block));
        }
        if (!created.isEmpty()) {
            recordScriptEdited(project, edition);
        }
        return created;
    }

    @Override
    @Transactional
    public boolean replaceLinkedDocumentBlocks(Integer projectId, Integer sourceDocumentId,
                                               List<CreateBlockBelowCommandModel> blocks) {
        if (projectId == null || sourceDocumentId == null) {
            return false;
        }
        List<Block> linked = blockRepository.findBySourceDocumentIdOrderByOrderAscIdAsc(sourceDocumentId);
        if (linked.isEmpty()) {
            return false;
        }
        linked = linked.stream()
                .filter(b -> b.getProject() != null && projectId.equals(b.getProject().getId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (linked.isEmpty()) {
            return false;
        }

        // Snapshot run membership by id so later deletes/inserts can't stale in-memory orders.
        List<List<Integer>> runIds = new ArrayList<>();
        for (List<Block> run : groupContiguousRuns(linked)) {
            List<Integer> ids = new ArrayList<>(run.size());
            for (Block block : run) {
                ids.add(block.getId());
            }
            runIds.add(ids);
        }

        List<CreateBlockBelowCommandModel> lines = blocks != null ? blocks : List.of();
        boolean changed = false;
        for (int r = runIds.size() - 1; r >= 0; r--) {
            if (replaceRunByIds(runIds.get(r), lines)) {
                changed = true;
            }
        }
        if (changed) {
            projectRepository.findById(projectId).ifPresent(this::recordScriptEdited);
        }
        return changed;
    }

    private List<List<Block>> groupContiguousRuns(List<Block> linkedOrdered) {
        List<List<Block>> runs = new ArrayList<>();
        List<Block> current = null;
        Integer prevOrder = null;
        for (Block block : linkedOrdered) {
            if (current == null || prevOrder == null || block.getOrder() != prevOrder + 1) {
                current = new ArrayList<>();
                runs.add(current);
            }
            current.add(block);
            prevOrder = block.getOrder();
        }
        return runs;
    }

    private boolean replaceRunByIds(List<Integer> ids, List<CreateBlockBelowCommandModel> lines) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        if (lines.isEmpty()) {
            for (int i = ids.size() - 1; i >= 0; i--) {
                deleteBlock(ids.get(i));
            }
            return true;
        }

        boolean changed = false;
        int shared = Math.min(ids.size(), lines.size());
        for (int i = 0; i < shared; i++) {
            Block block = blockRepository.findById(ids.get(i)).orElse(null);
            if (block == null) {
                continue;
            }
            CreateBlockBelowCommandModel line = lines.get(i);
            String content = PlainTextSanitizer.sanitize(line.getContent() != null ? line.getContent() : "");
            String type = normalizeBlockType(line.getType());
            boolean blockChanged = false;
            if (!content.equals(block.getContent() != null ? block.getContent() : "")) {
                block.setContent(content);
                blockChanged = true;
            }
            if (!type.equals(block.getType())) {
                block.setType(type);
                blockChanged = true;
            }
            if (block.getSourceDocumentId() == null
                    || !block.getSourceDocumentId().equals(line.getSourceDocumentId())) {
                block.setSourceDocumentId(line.getSourceDocumentId());
                blockChanged = true;
            }
            if (blockChanged) {
                blockRepository.save(block);
                changed = true;
            }
        }

        if (lines.size() > ids.size()) {
            Integer afterId = ids.get(ids.size() - 1);
            List<CreateBlockBelowCommandModel> extra = lines.subList(ids.size(), lines.size());
            List<Block> created = insertBlocksAfter(afterId, extra);
            if (!created.isEmpty()) {
                changed = true;
            }
        } else if (ids.size() > lines.size()) {
            for (int i = ids.size() - 1; i >= lines.size(); i--) {
                deleteBlock(ids.get(i));
            }
            changed = true;
        }
        return changed;
    }

    @Override
    @Transactional
    public Block saveEditBlockCommandModel(EditBlockCommandModel cmd) {
        Block block = blockRepository.findById(cmd.getId()).orElse(null);
        Person person = null;
        if (cmd.getPersonId() != null) {
            person = personRepository.findById(cmd.getPersonId()).orElse(null);
        }
        String content = PlainTextSanitizer.sanitize(cmd.getContent());

        if (Block.isCharacterCueType(block.getType())) {
            String characterName = normalizeCharacterCue(content);
            if (characterName != null) {
                person = findOrCreatePerson(characterName, block.getProject(), resolveEdition(block));
                content = characterName;
            } else {
                person = null;
                content = content != null ? content.trim() : "";
            }
        } else if (person == null && !block.isScene()) {
            String characterName = extractCharacterName(content);
            if (characterName != null) {
                person = findOrCreatePerson(characterName, block.getProject(), resolveEdition(block));
                content = stripCharacterName(content);
            }
        }

        block.setContent(content);
        block.setPerson(person);
        if (cmd.getTags() != null) {
            block.setTags(PlainTextSanitizer.sanitizeSingleLine(cmd.getTags()));
        }
        applyFormatting(block, cmd);
        blockRepository.save(block);
        recordScriptEdited(block.getProject(), resolveEdition(block));
        return block;
    }

    /**
     * Applies only the formatting the caller actually supplied. Absent (null)
     * fields keep the stored value, so a content-only save — the web form, or an
     * API client's debounced auto-save — never wipes alignment, font or styles.
     * Unrecognized alignment/font values are ignored here; callers that want to
     * report them validate before saving.
     */
    private void applyFormatting(Block block, EditBlockCommandModel cmd) {
        if (cmd.getTextAlign() != null) {
            String align = BlockFormatting.normalizeAlign(cmd.getTextAlign());
            if (align != null) {
                block.setTextAlign(align);
            }
        }
        if (cmd.getFont() != null) {
            String font = BlockFormatting.normalizeFont(cmd.getFont());
            if (font != null) {
                block.setFont(font);
            }
        }
        if (cmd.getTextBold() != null) {
            block.setTextBold(cmd.getTextBold());
        }
        if (cmd.getTextItalic() != null) {
            block.setTextItalic(cmd.getTextItalic());
        }
        if (cmd.getTextUnderline() != null) {
            block.setTextUnderline(cmd.getTextUnderline());
        }
    }

    @Override
    @Transactional
    public Block updateBlockTypeAndContent(Integer id, String type, String content, Integer personId, String tags) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null) {
            return null;
        }

        String previousType = block.getType();
        String normalized = type != null && Block.ELEMENT_TYPES.contains(type.toUpperCase())
                ? type.toUpperCase() : Block.TYPE_ACTION;
        block.setType(normalized);
        if (content != null) {
            block.setContent(PlainTextSanitizer.sanitize(content));
        }
        if (tags != null) {
            block.setTags(PlainTextSanitizer.sanitizeSingleLine(tags));
        }

        if (Block.TYPE_SCENE.equals(normalized)) {
            block.setPerson(null);
            block.setSceneDelimiter(false);
        } else if (Block.isCharacterCueType(normalized)) {
            Person person = personId != null ? personRepository.findById(personId).orElse(null) : null;
            if (person == null) {
                person = block.getPerson();
            }
            String characterName = person != null ? normalizeCharacterCue(person.getName()) : null;
            if (characterName == null) {
                characterName = normalizeCharacterCue(content != null ? content : block.getContent());
            }
            if (characterName != null) {
                block.setContent(characterName);
                block.setPerson(findOrCreatePerson(characterName, block.getProject(), resolveEdition(block)));
            } else {
                block.setPerson(null);
            }
        } else if (Block.TYPE_DIALOGUE.equals(normalized) && Block.isCharacterCueType(previousType)) {
            // Cue text becomes the speaker; clear content so it isn't duplicated as dialogue.
            Person person = personId != null ? personRepository.findById(personId).orElse(null) : null;
            if (person == null) {
                person = block.getPerson();
            }
            String characterName = person != null ? normalizeCharacterCue(person.getName()) : null;
            if (characterName == null) {
                characterName = normalizeCharacterCue(block.getContent());
            }
            if (characterName != null) {
                block.setPerson(findOrCreatePerson(characterName, block.getProject(), resolveEdition(block)));
            }
            block.setContent("");
        } else {
            Person person = personId != null ? personRepository.findById(personId).orElse(null) : null;
            block.setPerson(person);
        }

        Block saved = blockRepository.save(block);
        recordScriptEdited(block.getProject(), resolveEdition(block));
        return saved;
    }

    @Override
    @Transactional
    public Block createInitialBlock(Integer projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return null;
        }
        ScriptEdition edition = scriptEditionService.ensureDefaultEdition(projectId);
        if (edition == null || blockRepository.countByScriptEditionId(edition.getId()) > 0) {
            return null;
        }
        Block block = new Block();
        block.setContent("");
        block.setType(Block.TYPE_ACTION);
        block.setSceneDelimiter(false);
        block.setProject(project);
        block.setScriptEdition(edition);
        block.setBookmarked(false);
        block.setPinned(false);
        block.setOrder(1);
        return blockRepository.save(block);
    }

    @Override
    @Transactional
    public Block updateSceneName(Integer id, String name) {
        Block block = blockRepository.findById(id).orElse(null);
        block.setContent(PlainTextSanitizer.sanitizeSingleLine(name));
        Block saved = blockRepository.save(block);
        recordScriptEdited(block.getProject(), resolveEdition(block));
        return saved;
    }

    @Override
    @Transactional
    public Block updateCharacterName(Integer id, String name) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null || block.getProject() == null) {
            return block;
        }

        String trimmed = PlainTextSanitizer.sanitizeSingleLine(name);
        if (trimmed == null || trimmed.isEmpty()) {
            return block;
        }
        if (trimmed.length() > 60) {
            trimmed = trimmed.substring(0, 60).trim();
        }
        if (trimmed.isEmpty()) {
            return block;
        }

        Person person = findOrCreatePerson(trimmed, block.getProject(), resolveEdition(block));
        block.setPerson(person);
        if (Block.isCharacterCueType(block.getType())) {
            block.setContent(trimmed);
        }
        Block saved = blockRepository.save(block);
        recordScriptEdited(block.getProject(), resolveEdition(block));
        return saved;
    }

    @Override
    @Transactional
    public Block deleteBlock(Integer id) {
        Block block = blockRepository.findById(id).orElse(null);
        Project project = block.getProject();
        ScriptEdition edition = resolveEdition(block);
        deletedBlockRepository.save(DeletedBlock.capture(block, resolveCurrentUser(), LocalDateTime.now()));
        blockRepository.delete(block);
        if (edition != null) {
            blockRepository.decrementOrdersAbove(block.getOrder(), edition.getId());
        }
        recordScriptEdited(project, edition);
        return block;
    }

    @Override
    @Transactional
    public Block restoreBlock(DeletedBlock record) {
        Project project = record.getProject();
        ScriptEdition edition = record.getScriptEdition() != null
                ? record.getScriptEdition()
                : scriptEditionService.ensureDefaultEdition(project.getId());
        if (edition == null) {
            return null;
        }

        // The script kept moving after the delete, so the stored position may now
        // point past the end. Clamp it into the current range and open a gap there.
        int liveCount = blockRepository.countByScriptEditionId(edition.getId());
        int order = record.getOriginalOrder() != null ? record.getOriginalOrder() : liveCount + 1;
        order = Math.max(1, Math.min(order, liveCount + 1));
        blockRepository.incrementOrdersAbove(order - 1, edition.getId());

        Block block = new Block();
        block.setProject(project);
        block.setScriptEdition(edition);
        block.setOrder(order);
        block.setContent(record.getContent() != null ? record.getContent() : "");
        block.setType(normalizeBlockType(record.getType()));
        block.setSceneDelimiter(record.isSceneDelimiter());
        block.setBookmarked(record.isBookmarked());
        block.setPinned(record.isPinned());
        block.setTags(record.getTags());
        block.setTextAlign(record.getTextAlign());
        block.setFont(record.getFont());
        block.setHighlight(record.getHighlight());
        block.setTextBold(record.isTextBold());
        block.setTextItalic(record.isTextItalic());
        block.setTextUnderline(record.isTextUnderline());
        block.setSourceDocumentId(record.getSourceDocumentId());
        if (record.getPersonName() != null && !record.getPersonName().isBlank()) {
            block.setPerson(findOrCreatePerson(record.getPersonName(), project, edition));
        }

        Block saved = blockRepository.save(block);
        recordScriptEdited(project, edition);
        return saved;
    }

    @Override
    @Transactional
    public Block moveBlockUp(Integer id) {
        return swapWithNeighbour(id, -1);
    }

    @Override
    @Transactional
    public Block moveBlockDown(Integer id) {
        return swapWithNeighbour(id, 1);
    }

    /**
     * Swaps a block with its neighbour by position in the ordered list rather
     * than by {@code order ± 1}. Looking the neighbour up by order assumed a
     * dense, unique 1..N run: a gap made the move silently do nothing, and a
     * duplicate order made the single-result lookup throw.
     */
    private Block swapWithNeighbour(Integer id, int offset) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null) {
            return null;
        }
        Integer editionId = editionId(block);
        List<Block> ordered = editionId != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(editionId)
                : blockRepository.findByProjectIdOrderByOrderAscIdAsc(block.getProject().getId());

        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(block.getId())) {
                index = i;
                break;
            }
        }
        int neighbourIndex = index + offset;
        if (index < 0 || neighbourIndex < 0 || neighbourIndex >= ordered.size()) {
            return block;
        }

        // Renumber the whole edition so the swap also repairs any pre-existing
        // duplicate or gapped orders instead of propagating them.
        Block neighbour = ordered.get(neighbourIndex);
        ordered.set(index, neighbour);
        ordered.set(neighbourIndex, block);
        int order = 1;
        for (Block b : ordered) {
            b.setOrder(order++);
        }
        blockRepository.saveAll(ordered);
        recordScriptEdited(block.getProject(), resolveEdition(block));
        return block;
    }

    @Override
    @Transactional
    public Block moveBlockTo(Integer id, int newOrder) {
        Block block = blockRepository.findById(id).orElse(null);
        int currentOrder = block.getOrder();
        Integer scriptEditionId = editionId(block);
        if (newOrder == currentOrder || scriptEditionId == null) return block;

        if (newOrder < currentOrder) {
            blockRepository.incrementOrdersInRange(newOrder, currentOrder, scriptEditionId);
        } else {
            blockRepository.decrementOrdersInRange(currentOrder, newOrder, scriptEditionId);
        }
        block.setOrder(newOrder);
        blockRepository.save(block);
        recordScriptEdited(block.getProject(), resolveEdition(block));
        return block;
    }

    @Override
    @Transactional
    public Block toggleBookmark(Integer id) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null) {
            throw new IllegalArgumentException("Block not found");
        }
        block.setBookmarked(!block.isBookmarked());
        Block saved = blockRepository.save(block);
        recordScriptEdited(block.getProject(), resolveEdition(block));
        return saved;
    }

    @Override
    @Transactional
    public Block togglePinned(Integer id) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null) {
            throw new IllegalArgumentException("Block not found");
        }
        block.setPinned(!block.isPinned());
        Block saved = blockRepository.save(block);
        recordScriptEdited(block.getProject(), resolveEdition(block));
        return saved;
    }

    private List<CreatePersonViewModel> translateCreatePersonViewModel(List<Person> persons) {
        List<CreatePersonViewModel> vms = new ArrayList<>();
        for (Person person : persons) {
            CreatePersonViewModel vm = new CreatePersonViewModel();
            vm.setId(person.getId());
            vm.setName(person.getName());
            vms.add(vm);
        }
        return vms;
    }

    private int findCharacterLineIndex(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("@") && line.length() > 1) return i;
            if (i == 0) continue;
            if (line.isEmpty()) continue;
            if (!line.equals(line.toUpperCase())) continue;
            if (!line.matches(".*[A-Z].*")) continue;
            if (!lines[i - 1].trim().isEmpty()) continue;
            boolean emptyAfter = (i + 1 < lines.length && lines[i + 1].trim().isEmpty());
            if (emptyAfter) continue;
            return i;
        }
        return -1;
    }

    private String normalizeBlockType(String type) {
        return type != null && Block.ELEMENT_TYPES.contains(type.toUpperCase())
                ? type.toUpperCase() : Block.TYPE_ACTION;
    }

    private String characterNameFromLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("@")) return trimmed.substring(1).trim();
        return trimmed;
    }

    private String normalizeCharacterCue(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1).trim();
        }
        trimmed = trimmed.replaceAll("\\^(\\*)?", "").trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 60) {
            trimmed = trimmed.substring(0, 60).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extractCharacterName(String content) {
        if (content == null) return null;
        String[] lines = content.split("\n", -1);
        int idx = findCharacterLineIndex(lines);
        if (idx < 0) return null;
        return characterNameFromLine(lines[idx]);
    }

    private String stripCharacterName(String content) {
        if (content == null) return content;
        String[] lines = content.split("\n", -1);
        int idx = findCharacterLineIndex(lines);
        if (idx < 0) return content;
        boolean isForced = lines[idx].trim().startsWith("@");
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < lines.length; j++) {
            if (j == idx) continue;
            if (!isForced && j == idx - 1 && lines[j].trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(lines[j]);
        }
        return sb.toString().trim();
    }


    /** The signed-in user, or null for the dev auto-login / unauthenticated paths. */
    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return null;
        }
        return userService.readByUsername(username);
    }

    private ScriptEdition resolveEdition(Block block) {
        if (block != null && block.getScriptEdition() != null) {
            return block.getScriptEdition();
        }
        if (block != null && block.getProject() != null) {
            return scriptEditionService.getDefaultForProject(block.getProject().getId());
        }
        return null;
    }

    private ScriptEdition resolveEdition(Project project) {
        if (project == null) {
            return null;
        }
        return scriptEditionService.getDefaultForProject(project.getId());
    }

    private Integer editionId(Block block) {
        ScriptEdition edition = resolveEdition(block);
        return edition != null ? edition.getId() : null;
    }

    private Person findOrCreatePerson(String characterName, Project project) {
        return findOrCreatePerson(characterName, project, resolveEdition(project));
    }

    private Person findOrCreatePerson(String characterName, Project project, ScriptEdition edition) {
        if (edition == null) {
            edition = resolveEdition(project);
        }
        List<Person> persons = edition != null
                ? personRepository.findByScriptEditionIdOrderByNameAsc(edition.getId())
                : personRepository.findByProjectIdOrderByNameAsc(project.getId());
        for (Person person : persons) {
            if (person.getName().equalsIgnoreCase(characterName)) {
                return person;
            }
        }
        Person newPerson = new Person();
        newPerson.setName(PlainTextSanitizer.sanitizeSingleLine(characterName));
        newPerson.setFullName(PlainTextSanitizer.sanitizeSingleLine(characterName));
        newPerson.setProject(project);
        newPerson.setScriptEdition(edition);
        return personRepository.save(newPerson);
    }

    @Override
    @Transactional
    public void addTagsToBlocks(List<Integer> ids, String tagsToAdd) {
        if (ids == null || ids.isEmpty() || tagsToAdd == null || tagsToAdd.trim().isEmpty()) {
            return;
        }

        String[] newTagsArray = tagsToAdd.split(",");
        List<String> newTagsList = new ArrayList<>();
                for (String t : newTagsArray) {
            String trimmed = PlainTextSanitizer.sanitizeSingleLine(t);
            if (trimmed != null && !trimmed.isEmpty()) {
                newTagsList.add(trimmed);
            }
        }

        if (newTagsList.isEmpty()) {
            return;
        }

        java.util.Set<Integer> touchedProjectIds = new java.util.HashSet<>();
        for (Integer id : ids) {
            Block block = blockRepository.findById(id).orElse(null);
            if (block != null) {
                String existingTags = block.getTags();
                List<String> combinedTags = new ArrayList<>();
                if (existingTags != null && !existingTags.trim().isEmpty()) {
                    String[] existingArray = existingTags.split(",");
                    for (String t : existingArray) {
                        String trimmed = t.trim();
                        if (!trimmed.isEmpty()) {
                            combinedTags.add(trimmed);
                        }
                    }
                }

                for (String newTag : newTagsList) {
                    boolean exists = false;
                    for (String existingTag : combinedTags) {
                        if (existingTag.equalsIgnoreCase(newTag)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        combinedTags.add(newTag);
                    }
                }

                block.setTags(String.join(", ", combinedTags));
                blockRepository.save(block);
                if (block.getProject() != null) {
                    touchedProjectIds.add(block.getProject().getId());
                }
            }
        }
        for (Integer projectId : touchedProjectIds) {
            projectRepository.findById(projectId).ifPresent(this::recordScriptEdited);
        }
    }

    @Override
    @Transactional
    public void setBlockTypes(List<Integer> ids, String type) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String normalized = type != null && Block.ELEMENT_TYPES.contains(type.toUpperCase())
                ? type.toUpperCase() : Block.TYPE_ACTION;
        java.util.Set<Integer> touchedProjectIds = new java.util.HashSet<>();
        for (Integer id : ids) {
            Block block = blockRepository.findById(id).orElse(null);
            if (block != null && !normalized.equals(block.getType())) {
                String previousType = block.getType();
                block.setType(normalized);
                if (Block.TYPE_SCENE.equals(normalized)) {
                    // Inline scene headings keep their row position; only created/imported
                    // scene delimiters open a new scene section in the editor.
                    block.setPerson(null);
                    block.setSceneDelimiter(false);
                } else if (Block.isCharacterCueType(normalized)) {
                    String characterName = null;
                    if (block.getPerson() != null) {
                        characterName = normalizeCharacterCue(block.getPerson().getName());
                    }
                    if (characterName == null) {
                        characterName = normalizeCharacterCue(block.getContent());
                    }
                    if (characterName != null) {
                        block.setContent(characterName);
                        block.setPerson(findOrCreatePerson(characterName, block.getProject(), resolveEdition(block)));
                    }
                } else if (Block.TYPE_DIALOGUE.equals(normalized) && Block.isCharacterCueType(previousType)) {
                    String characterName = null;
                    if (block.getPerson() != null) {
                        characterName = normalizeCharacterCue(block.getPerson().getName());
                    }
                    if (characterName == null) {
                        characterName = normalizeCharacterCue(block.getContent());
                    }
                    if (characterName != null) {
                        block.setPerson(findOrCreatePerson(characterName, block.getProject(), resolveEdition(block)));
                    }
                    block.setContent("");
                }
                blockRepository.save(block);
                if (block.getProject() != null) {
                    touchedProjectIds.add(block.getProject().getId());
                }
            }
        }
        for (Integer projectId : touchedProjectIds) {
            projectRepository.findById(projectId).ifPresent(this::recordScriptEdited);
        }
    }

    @Override
    @Transactional
    public void setBlockAlignments(List<Integer> ids, String align) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String normalized = align != null && Block.TEXT_ALIGNS.contains(align.toUpperCase())
                ? align.toUpperCase() : Block.ALIGN_LEFT;
        java.util.Set<Integer> touchedProjectIds = new java.util.HashSet<>();
        for (Integer id : ids) {
            Block block = blockRepository.findById(id).orElse(null);
            if (block != null && !normalized.equals(block.getTextAlign())) {
                block.setTextAlign(normalized);
                blockRepository.save(block);
                if (block.getProject() != null) {
                    touchedProjectIds.add(block.getProject().getId());
                }
            }
        }
        for (Integer projectId : touchedProjectIds) {
            projectRepository.findById(projectId).ifPresent(this::recordScriptEdited);
        }
    }

    @Override
    @Transactional
    public void setBlockFonts(List<Integer> ids, String font) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String normalized = font != null && Block.FONTS.contains(font.toUpperCase())
                ? font.toUpperCase() : null;
        java.util.Set<Integer> touchedProjectIds = new java.util.HashSet<>();
        for (Integer id : ids) {
            Block block = blockRepository.findById(id).orElse(null);
            if (block != null && !java.util.Objects.equals(normalized, block.getFont())) {
                block.setFont(normalized);
                blockRepository.save(block);
                if (block.getProject() != null) {
                    touchedProjectIds.add(block.getProject().getId());
                }
            }
        }
        for (Integer projectId : touchedProjectIds) {
            projectRepository.findById(projectId).ifPresent(this::recordScriptEdited);
        }
    }

    @Override
    @Transactional
    public void setBlockHighlights(List<Integer> ids, String highlight) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String normalized = Block.normalizeHighlight(highlight);
        java.util.Set<Integer> touchedProjectIds = new java.util.HashSet<>();
        for (Integer id : ids) {
            Block block = blockRepository.findById(id).orElse(null);
            if (block != null && !java.util.Objects.equals(normalized, block.getHighlight())) {
                block.setHighlight(normalized);
                blockRepository.save(block);
                if (block.getProject() != null) {
                    touchedProjectIds.add(block.getProject().getId());
                }
            }
        }
        for (Integer projectId : touchedProjectIds) {
            projectRepository.findById(projectId).ifPresent(this::recordScriptEdited);
        }
    }

    @Override
    @Transactional
    public void toggleBlockTextStyles(List<Integer> ids, String style) {
        if (ids == null || ids.isEmpty() || style == null) {
            return;
        }
        String normalized = style.toUpperCase();
        if (!Block.TEXT_STYLES.contains(normalized)) {
            return;
        }
        java.util.Set<Integer> touchedProjectIds = new java.util.HashSet<>();
        for (Integer id : ids) {
            Block block = blockRepository.findById(id).orElse(null);
            if (block == null) {
                continue;
            }
            switch (normalized) {
                case Block.STYLE_BOLD -> block.setTextBold(!block.isTextBold());
                case Block.STYLE_ITALIC -> block.setTextItalic(!block.isTextItalic());
                case Block.STYLE_UNDERLINE -> block.setTextUnderline(!block.isTextUnderline());
                default -> { continue; }
            }
            blockRepository.save(block);
            if (block.getProject() != null) {
                touchedProjectIds.add(block.getProject().getId());
            }
        }
        for (Integer projectId : touchedProjectIds) {
            projectRepository.findById(projectId).ifPresent(this::recordScriptEdited);
        }
    }

    /**
     * Compiles the find term as a literal — regex search is deliberately not exposed to users.
     */
    private java.util.regex.Pattern buildFindPattern(String find, boolean matchCase, boolean wholeWord) {
        String quoted = java.util.regex.Pattern.quote(find);
        if (wholeWord) {
            quoted = "\\b" + quoted + "\\b";
        }
        int flags = matchCase ? 0
                : (java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
        return java.util.regex.Pattern.compile(quoted, flags);
    }

    @Override
    @Transactional
    public Block replaceOccurrenceInBlock(Integer id, String find, String replace,
                                          boolean matchCase, boolean wholeWord, int occurrence) {
        if (id == null || find == null || find.isEmpty() || occurrence < 0) {
            return null;
        }
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null) {
            return null;
        }
        String original = block.getContent();
        if (original == null || original.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher matcher = buildFindPattern(find, matchCase, wholeWord).matcher(original);
        int index = 0;
        while (matcher.find()) {
            if (index == occurrence) {
                String updated = PlainTextSanitizer.sanitize(
                        original.substring(0, matcher.start())
                                + (replace != null ? replace : "")
                                + original.substring(matcher.end()));
                if (updated == null || updated.equals(original)) {
                    return block;
                }
                block.setContent(updated);
                blockRepository.save(block);
                recordScriptEdited(block.getProject(), resolveEdition(block));
                return block;
            }
            index++;
        }
        return null;
    }

    @Override
    @Transactional
    public int replaceInBlocks(List<Integer> ids, String find, String replace,
                               boolean matchCase, boolean wholeWord, boolean includeCharacterCues) {
        if (ids == null || ids.isEmpty() || find == null || find.isEmpty()) {
            return 0;
        }
        java.util.regex.Pattern pattern = buildFindPattern(find, matchCase, wholeWord);
        String replacement = java.util.regex.Matcher.quoteReplacement(replace != null ? replace : "");

        java.util.Set<Integer> touchedProjectIds = new java.util.HashSet<>();
        int changed = 0;
        for (Integer id : ids) {
            Block block = blockRepository.findById(id).orElse(null);
            if (block == null) {
                continue;
            }
            // Cue content mirrors the block's person; rewriting it here would desync the two.
            if (!includeCharacterCues && Block.isCharacterCueType(block.getType())) {
                continue;
            }
            String original = block.getContent();
            if (original == null || original.isEmpty()) {
                continue;
            }
            String updated = PlainTextSanitizer.sanitize(pattern.matcher(original).replaceAll(replacement));
            if (updated == null || updated.equals(original)) {
                continue;
            }
            block.setContent(updated);
            blockRepository.save(block);
            changed++;
            if (block.getProject() != null) {
                touchedProjectIds.add(block.getProject().getId());
            }
        }
        for (Integer projectId : touchedProjectIds) {
            projectRepository.findById(projectId).ifPresent(this::recordScriptEdited);
        }
        return changed;
    }

    @Override
    @Transactional
    public void deleteBlocks(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        java.util.Set<Integer> projectIds = new java.util.HashSet<>();
        java.util.Set<Integer> editionIds = new java.util.HashSet<>();
        User deletedBy = resolveCurrentUser();
        LocalDateTime deletedAt = LocalDateTime.now();
        for (Integer id : ids) {
            blockRepository.findById(id).ifPresent(block -> {
                if (block.getProject() != null) {
                    projectIds.add(block.getProject().getId());
                }
                if (block.getScriptEdition() != null) {
                    editionIds.add(block.getScriptEdition().getId());
                }
                deletedBlockRepository.save(DeletedBlock.capture(block, deletedBy, deletedAt));
            });
        }
        blockRepository.deleteAllById(ids);
        for (Integer editionId : editionIds) {
            List<Block> remainingBlocks = blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(editionId);
            int order = 1;
            for (Block b : remainingBlocks) {
                b.setOrder(order++);
                blockRepository.save(b);
            }
        }
        for (Integer projectId : projectIds) {
            projectRepository.findById(projectId).ifPresent(this::recordScriptEdited);
        }
    }

    private void recordScriptEdited(Project project) {
        recordScriptEdited(project, null);
    }

    private void recordScriptEdited(Project project, ScriptEdition edition) {
        if (project == null || project.getId() == null) {
            return;
        }
        if (projectUndoRedoService.isRecordingSuppressed()) {
            return;
        }
        if (edition != null) {
            scriptEditionService.touchEdition(edition);
        } else {
            ScriptEdition defaultEdition = scriptEditionService.getDefaultForProject(project.getId());
            if (defaultEdition != null) {
                scriptEditionService.touchEdition(defaultEdition);
            }
        }
        projectActivityService.recordForCurrentUser(
                project.getId(),
                ProjectActivity.ACTION_SCRIPT_EDITED,
                "edited the script",
                ProjectActivity.ENTITY_BLOCK,
                null);
    }
}
