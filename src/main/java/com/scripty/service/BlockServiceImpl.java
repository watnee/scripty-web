package com.scripty.service;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.Scene;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.SceneRepository;
import com.scripty.viewmodel.block.createblock.CreateBlockViewModel;
import com.scripty.viewmodel.block.createblock.CreatePersonViewModel;
import com.scripty.viewmodel.block.createblockbelow.CreateBlockBelowViewModel;
import com.scripty.viewmodel.block.editblock.EditBlockViewModel;
import com.scripty.viewmodel.block.editblock.EditPersonViewModel;
import com.scripty.viewmodel.scene.sceneprofile.BlockViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockServiceImpl implements BlockService {

    private final BlockRepository blockRepository;
    private final PersonRepository personRepository;
    private final SceneRepository sceneRepository;
    private final ProjectRepository projectRepository;

    @Autowired
    public BlockServiceImpl(BlockRepository blockRepository,
                            PersonRepository personRepository,
                            SceneRepository sceneRepository,
                            ProjectRepository projectRepository) {
        this.blockRepository = blockRepository;
        this.personRepository = personRepository;
        this.sceneRepository = sceneRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public Block read(Integer id) {
        return blockRepository.findById(id).orElse(null);
    }

    @Override
    public CreateBlockViewModel getCreateBlockViewModel(Integer sceneId) {
        CreateBlockViewModel vm = new CreateBlockViewModel();
        Scene scene = sceneRepository.findById(sceneId).orElse(null);
        Project project = projectRepository.findBySceneId(scene.getId());

        CreateBlockCommandModel commandModel = new CreateBlockCommandModel();
        commandModel.setSceneId(scene.getId());
        vm.setCreateBlockCommandModel(commandModel);

        List<Person> persons = personRepository.findByProjectIdOrderByNameAsc(project.getId());
        vm.setSceneId(scene.getId());
        vm.setPersons(translateCreatePersonViewModel(persons));
        return vm;
    }

    @Override
    public CreateBlockBelowViewModel getCreateBlockBelowViewModel(Integer id) {
        CreateBlockBelowViewModel vm = new CreateBlockBelowViewModel();
        Block existingBlock = blockRepository.findById(id).orElse(null);
        Scene scene = sceneRepository.findById(existingBlock.getScene().getId()).orElse(null);
        Project project = projectRepository.findBySceneId(scene.getId());

        CreateBlockBelowCommandModel commandModel = new CreateBlockBelowCommandModel();
        commandModel.setId(existingBlock.getId());
        commandModel.setSceneId(scene.getId());
        vm.setCreateBlockBelowCommandModel(commandModel);

        List<Person> persons = personRepository.findByProjectIdOrderByNameAsc(project.getId());
        vm.setSceneId(scene.getId());
        vm.setPersons(translateCreatePersonViewModel(persons));
        return vm;
    }

    @Override
    public EditBlockViewModel getEditBlockViewModel(Integer id) {
        EditBlockViewModel vm = new EditBlockViewModel();
        Block existingBlock = blockRepository.findById(id).orElse(null);
        List<Person> allPersons = personRepository.findAll();
        Scene scene = sceneRepository.findById(existingBlock.getScene().getId()).orElse(null);

        vm.setSceneId(scene.getId());

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
        commandModel.setSceneId(scene.getId());
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
        Scene scene = sceneRepository.findById(cmd.getSceneId()).orElse(null);
        String content = cmd.getContent();

        if (person == null) {
            String characterName = extractCharacterName(content);
            if (characterName != null) {
                Project project = projectRepository.findBySceneId(scene.getId());
                person = findOrCreatePerson(characterName, project);
                content = stripCharacterName(content);
            }
        }

        block.setContent(content);
        if (person != null) block.setPerson(person);
        block.setScene(scene);

        int order = blockRepository.countBySceneId(scene.getId()) + 1;
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
        Scene scene = sceneRepository.findById(existingBlock.getScene().getId()).orElse(null);
        String content = cmd.getContent();

        if (person == null) {
            String characterName = extractCharacterName(content);
            if (characterName != null) {
                Project project = projectRepository.findBySceneId(scene.getId());
                person = findOrCreatePerson(characterName, project);
                content = stripCharacterName(content);
            }
        }

        Block block = new Block();
        block.setContent(content);
        if (person != null) block.setPerson(person);
        block.setScene(scene);

        int newOrder = existingBlock.getOrder() + 1;
        blockRepository.incrementOrdersAbove(existingBlock.getOrder(), scene.getId());
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
        Scene scene = sceneRepository.findById(cmd.getSceneId()).orElse(null);
        String content = cmd.getContent();

        if (person == null) {
            String characterName = extractCharacterName(content);
            if (characterName != null) {
                Project project = projectRepository.findBySceneId(scene.getId());
                person = findOrCreatePerson(characterName, project);
                content = stripCharacterName(content);
            }
        }

        block.setContent(content);
        block.setPerson(person);
        block.setScene(scene);
        blockRepository.save(block);
        return block;
    }

    @Override
    @Transactional
    public Block deleteBlock(Integer id) {
        Block block = blockRepository.findById(id).orElse(null);
        blockRepository.delete(block);
        blockRepository.decrementOrdersAbove(block.getOrder(), block.getScene().getId());
        return block;
    }

    @Override
    @Transactional
    public Block moveBlockUp(Integer id) {
        Block block = blockRepository.findById(id).orElse(null);
        Block blockAbove = blockRepository
            .findBySceneIdAndOrder(block.getScene().getId(), block.getOrder() - 1)
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
            .findBySceneIdAndOrder(block.getScene().getId(), block.getOrder() + 1)
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
        int sceneId = block.getScene().getId();
        if (newOrder == currentOrder) return block;

        if (newOrder < currentOrder) {
            blockRepository.incrementOrdersInRange(newOrder, currentOrder, sceneId);
        } else {
            blockRepository.decrementOrdersInRange(currentOrder, newOrder, sceneId);
        }
        block.setOrder(newOrder);
        blockRepository.save(block);
        return block;
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
}
