package com.scripty.service;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.viewmodel.block.BlockViewModel;
import com.scripty.viewmodel.block.createblock.CreateBlockViewModel;
import com.scripty.viewmodel.block.createblock.CreatePersonViewModel;
import com.scripty.viewmodel.block.createblockbelow.CreateBlockBelowViewModel;
import com.scripty.viewmodel.block.editblock.EditBlockViewModel;
import com.scripty.viewmodel.block.editblock.EditPersonViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockServiceImpl implements BlockService {

    private final BlockRepository blockRepository;
    private final PersonRepository personRepository;
    private final ProjectRepository projectRepository;

    @Autowired
    public BlockServiceImpl(BlockRepository blockRepository,
                            PersonRepository personRepository,
                            ProjectRepository projectRepository) {
        this.blockRepository = blockRepository;
        this.personRepository = personRepository;
        this.projectRepository = projectRepository;
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

        List<Person> persons = personRepository.findByProjectIdOrderByNameAsc(project.getId());
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

        List<Person> persons = personRepository.findByProjectIdOrderByNameAsc(project.getId());
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

        List<Person> allPersons = personRepository.findAll();
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
        String content = cmd.getContent();

        if (person == null) {
            String characterName = extractCharacterName(content);
            if (characterName != null) {
                person = findOrCreatePerson(characterName, project);
                content = stripCharacterName(content);
            }
        }

        block.setContent(content);
        if (person != null) block.setPerson(person);
        block.setProject(project);
        block.setBookmarked(false);
        block.setPinned(false);

        int order = blockRepository.countByProjectId(project.getId()) + 1;
        block.setOrder(order);
        return blockRepository.save(block);
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
        String content = cmd.getContent();

        if (person == null) {
            String characterName = extractCharacterName(content);
            if (characterName != null) {
                person = findOrCreatePerson(characterName, project);
                content = stripCharacterName(content);
            }
        }

        Block block = new Block();
        block.setContent(content);
        if (person != null) block.setPerson(person);
        block.setProject(project);
        block.setBookmarked(false);
        block.setPinned(false);

        int newOrder = existingBlock.getOrder() + 1;
        blockRepository.incrementOrdersAbove(existingBlock.getOrder(), project.getId());
        block.setOrder(newOrder);
        return blockRepository.save(block);
    }

    @Override
    @Transactional
    public Block saveEditBlockCommandModel(EditBlockCommandModel cmd) {
        Block block = blockRepository.findById(cmd.getId()).orElse(null);
        Person person = null;
        if (cmd.getPersonId() != null) {
            person = personRepository.findById(cmd.getPersonId()).orElse(null);
        }
        String content = cmd.getContent();

        if (person == null && !block.isScene()) {
            String characterName = extractCharacterName(content);
            if (characterName != null) {
                person = findOrCreatePerson(characterName, block.getProject());
                content = stripCharacterName(content);
            }
        }

        block.setContent(content);
        block.setPerson(person);
        if (cmd.getTags() != null) {
            block.setTags(cmd.getTags());
        }
        blockRepository.save(block);
        return block;
    }

    @Override
    @Transactional
    public Block createSceneBlock(Integer projectId, String name) {
        Project project = projectRepository.findById(projectId).orElse(null);
        Block block = new Block();
        block.setContent(name);
        block.setType(Block.TYPE_SCENE);
        block.setSceneDelimiter(true);
        block.setProject(project);
        block.setBookmarked(false);
        block.setPinned(false);

        int order = blockRepository.countByProjectId(project.getId()) + 1;
        block.setOrder(order);
        return blockRepository.save(block);
    }

    @Override
    @Transactional
    public Block updateSceneName(Integer id, String name) {
        Block block = blockRepository.findById(id).orElse(null);
        block.setContent(name);
        return blockRepository.save(block);
    }

    @Override
    @Transactional
    public Block deleteBlock(Integer id) {
        Block block = blockRepository.findById(id).orElse(null);
        blockRepository.delete(block);
        blockRepository.decrementOrdersAbove(block.getOrder(), block.getProject().getId());
        return block;
    }

    @Override
    @Transactional
    public Block moveBlockUp(Integer id) {
        Block block = blockRepository.findById(id).orElse(null);
        Block blockAbove = blockRepository
            .findByProjectIdAndOrder(block.getProject().getId(), block.getOrder() - 1)
            .orElse(null);
        if (blockAbove != null) {
            int tempOrder = blockAbove.getOrder();
            blockAbove.setOrder(block.getOrder());
            block.setOrder(tempOrder);
            blockRepository.save(blockAbove);
            blockRepository.save(block);
        }
        return block;
    }

    @Override
    @Transactional
    public Block moveBlockDown(Integer id) {
        Block block = blockRepository.findById(id).orElse(null);
        Block blockBelow = blockRepository
            .findByProjectIdAndOrder(block.getProject().getId(), block.getOrder() + 1)
            .orElse(null);
        if (blockBelow != null) {
            int tempOrder = blockBelow.getOrder();
            blockBelow.setOrder(block.getOrder());
            block.setOrder(tempOrder);
            blockRepository.save(blockBelow);
            blockRepository.save(block);
        }
        return block;
    }

    @Override
    @Transactional
    public Block moveBlockTo(Integer id, int newOrder) {
        Block block = blockRepository.findById(id).orElse(null);
        int currentOrder = block.getOrder();
        int projectId = block.getProject().getId();
        if (newOrder == currentOrder) return block;

        if (newOrder < currentOrder) {
            blockRepository.incrementOrdersInRange(newOrder, currentOrder, projectId);
        } else {
            blockRepository.decrementOrdersInRange(currentOrder, newOrder, projectId);
        }
        block.setOrder(newOrder);
        blockRepository.save(block);
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
        return blockRepository.save(block);
    }

    @Override
    @Transactional
    public Block togglePinned(Integer id) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null) {
            throw new IllegalArgumentException("Block not found");
        }
        block.setPinned(!block.isPinned());
        return blockRepository.save(block);
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

    private String characterNameFromLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("@")) return trimmed.substring(1).trim();
        return trimmed;
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

    private Person findOrCreatePerson(String characterName, Project project) {
        List<Person> persons = personRepository.findByProjectIdOrderByNameAsc(project.getId());
        for (Person person : persons) {
            if (person.getName().equalsIgnoreCase(characterName)) {
                return person;
            }
        }
        Person newPerson = new Person();
        newPerson.setName(characterName);
        newPerson.setFullName(characterName);
        newPerson.setProject(project);
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
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                newTagsList.add(trimmed);
            }
        }

        if (newTagsList.isEmpty()) {
            return;
        }

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
            }
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
        for (Integer id : ids) {
            Block block = blockRepository.findById(id).orElse(null);
            if (block != null && !normalized.equals(block.getType())) {
                block.setType(normalized);
                if (Block.TYPE_SCENE.equals(normalized)) {
                    // Inline scene headings keep their row position; only created/imported
                    // scene delimiters open a new scene section in the editor.
                    block.setPerson(null);
                    block.setSceneDelimiter(false);
                }
                blockRepository.save(block);
            }
        }
    }

    @Override
    @Transactional
    public void deleteBlocks(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        java.util.Set<Integer> projectIds = new java.util.HashSet<>();
        for (Integer id : ids) {
            blockRepository.findById(id).ifPresent(block -> {
                projectIds.add(block.getProject().getId());
            });
        }
        blockRepository.deleteAllById(ids);
        for (Integer projectId : projectIds) {
            List<Block> remainingBlocks = blockRepository.findByProjectIdOrderByOrderAsc(projectId);
            int order = 1;
            for (Block b : remainingBlocks) {
                b.setOrder(order++);
                blockRepository.save(b);
            }
        }
    }
}
