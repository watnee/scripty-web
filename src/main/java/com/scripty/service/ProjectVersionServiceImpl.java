package com.scripty.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectVersion;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ProjectVersionRepository;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;
import com.scripty.viewmodel.project.versionhistory.VersionViewModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectVersionServiceImpl implements ProjectVersionService {

    private final ProjectRepository projectRepository;
    private final ProjectVersionRepository projectVersionRepository;
    private final BlockRepository blockRepository;
    private final PersonRepository personRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProjectVersionServiceImpl(ProjectRepository projectRepository,
                                     ProjectVersionRepository projectVersionRepository,
                                     BlockRepository blockRepository,
                                     PersonRepository personRepository,
                                     ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.projectVersionRepository = projectVersionRepository;
        this.blockRepository = blockRepository;
        this.personRepository = personRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public VersionHistoryViewModel getVersionHistoryViewModel(Integer projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        List<ProjectVersion> versions = projectVersionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        VersionHistoryViewModel vm = new VersionHistoryViewModel();
        vm.setProjectId(project.getId());
        vm.setProjectTitle(project.getTitle());

        List<VersionViewModel> versionVMs = new ArrayList<>();
        for (ProjectVersion version : versions) {
            VersionViewModel vvm = new VersionViewModel();
            vvm.setId(version.getId());
            vvm.setLabel(version.getLabel());
            vvm.setCreatedAt(version.getCreatedAt());
            try {
                Map<String, Object> snapshot = objectMapper.readValue(version.getSnapshotJson(), Map.class);
                List<?> persons = (List<?>) snapshot.get("persons");
                vvm.setCharacterCount(persons != null ? persons.size() : 0);

                List<?> blocks = (List<?>) snapshot.get("blocks");
                if (blocks != null) {
                    // Current format: one flat list; scene-type blocks are the scene headings.
                    int sceneCount = 0;
                    int blockCount = 0;
                    for (Object b : blocks) {
                        Map<?, ?> blockMap = (Map<?, ?>) b;
                        if (Block.TYPE_SCENE.equals(blockMap.get("type"))) {
                            sceneCount++;
                        } else {
                            blockCount++;
                        }
                    }
                    vvm.setSceneCount(sceneCount);
                    vvm.setBlockCount(blockCount);
                } else {
                    // Legacy format: nested scenes, each with its own block list.
                    List<?> scenes = (List<?>) snapshot.get("scenes");
                    vvm.setSceneCount(scenes != null ? scenes.size() : 0);
                    int blockCount = 0;
                    if (scenes != null) {
                        for (Object s : scenes) {
                            Map<?, ?> sceneMap = (Map<?, ?>) s;
                            List<?> sceneBlocks = (List<?>) sceneMap.get("blocks");
                            if (sceneBlocks != null) blockCount += sceneBlocks.size();
                        }
                    }
                    vvm.setBlockCount(blockCount);
                }
            } catch (JsonProcessingException e) {
                vvm.setSceneCount(0);
                vvm.setBlockCount(0);
                vvm.setCharacterCount(0);
            }
            versionVMs.add(vvm);
        }
        vm.setVersions(versionVMs);
        return vm;
    }

    @Override
    @Transactional
    public ProjectVersion createVersion(Integer projectId, String label) {
        return createVersionFromSnapshot(projectId, label, buildSnapshotJson(projectId));
    }

    private ProjectVersion createVersionFromSnapshot(Integer projectId, String label, String snapshotJson) {
        Project project = projectRepository.findById(projectId).orElse(null);
        ProjectVersion version = new ProjectVersion();
        version.setProject(project);
        version.setLabel(label);
        version.setCreatedAt(LocalDateTime.now());
        version.setSnapshotJson(snapshotJson);
        return projectVersionRepository.save(version);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String buildSnapshotJson(Integer projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        List<Block> blocks = blockRepository.findByProjectIdOrderByOrderAsc(projectId);
        List<Person> persons = personRepository.findByProjectIdOrderByNameAsc(projectId);

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("title", project.getTitle());
        snapshot.put("screenplayTitle", project.getScreenplayTitle());
        snapshot.put("writers", project.getWriters());
        snapshot.put("contactInfo", project.getContactInfo());

        List<Map<String, Object>> personSnapshots = new ArrayList<>();
        for (Person person : persons) {
            Map<String, Object> p = new HashMap<>();
            p.put("name", person.getName());
            p.put("fullName", person.getFullName());
            p.put("actorId", person.getActor() != null ? person.getActor().getId() : null);
            p.put("originalId", person.getId());
            personSnapshots.add(p);
        }
        snapshot.put("persons", personSnapshots);

        List<Map<String, Object>> blockSnapshots = new ArrayList<>();
        for (Block block : blocks) {
            Map<String, Object> b = new HashMap<>();
            b.put("order", block.getOrder());
            b.put("content", block.getContent());
            b.put("type", block.getType());
            b.put("sceneDelimiter", block.isSceneDelimiter());
            b.put("bookmarked", block.isBookmarked());
            b.put("pinned", block.isPinned());
            b.put("tags", block.getTags());
            b.put("textAlign", block.getTextAlign());
            b.put("textBold", block.isTextBold());
            b.put("textItalic", block.isTextItalic());
            b.put("textUnderline", block.isTextUnderline());
            b.put("personOriginalId", block.getPerson() != null ? block.getPerson().getId() : null);
            blockSnapshots.add(b);
        }
        snapshot.put("blocks", blockSnapshots);

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize project snapshot", e);
        }
    }

    @Override
    @Transactional
    public void applySnapshotJson(Integer projectId, String snapshotJson) {
        Map<String, Object> snapshot;
        try {
            snapshot = objectMapper.readValue(snapshotJson, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize project snapshot", e);
        }
        applySnapshot(projectId, snapshot);
    }

    @SuppressWarnings("unchecked")
    private void applySnapshot(Integer projectId, Map<String, Object> snapshot) {
        Project project = projectRepository.findById(projectId).orElse(null);

        List<Block> existingBlocks = blockRepository.findByProjectIdOrderByOrderAsc(projectId);
        blockRepository.deleteAll(existingBlocks);

        List<Person> existingPersons = personRepository.findByProjectIdOrderByNameAsc(projectId);
        personRepository.deleteAll(existingPersons);

        project.setTitle((String) snapshot.get("title"));
        if (snapshot.containsKey("screenplayTitle")) {
            project.setScreenplayTitle((String) snapshot.get("screenplayTitle"));
        }
        if (snapshot.containsKey("writers")) {
            project.setWriters((String) snapshot.get("writers"));
        }
        if (snapshot.containsKey("contactInfo")) {
            project.setContactInfo((String) snapshot.get("contactInfo"));
        }
        projectRepository.save(project);

        List<Map<String, Object>> personSnapshots = (List<Map<String, Object>>) snapshot.get("persons");
        Map<Integer, Person> originalIdToNewPerson = new HashMap<>();
        if (personSnapshots != null) {
            for (Map<String, Object> ps : personSnapshots) {
                Person person = new Person();
                person.setName((String) ps.get("name"));
                person.setFullName((String) ps.get("fullName"));
                person.setProject(project);
                person = personRepository.save(person);
                Integer originalId = (Integer) ps.get("originalId");
                if (originalId != null) {
                    originalIdToNewPerson.put(originalId, person);
                }
            }
        }

        List<Map<String, Object>> blockSnapshots = (List<Map<String, Object>>) snapshot.get("blocks");
        if (blockSnapshots != null) {
            for (Map<String, Object> bs : blockSnapshots) {
                restoreBlock(project, (Integer) bs.get("order"), (String) bs.get("content"),
                        (String) bs.get("type"), (Integer) bs.get("personOriginalId"), originalIdToNewPerson,
                        (Boolean) bs.get("bookmarked"), (Boolean) bs.get("pinned"), (String) bs.get("tags"),
                        (Boolean) bs.get("sceneDelimiter"), (String) bs.get("textAlign"),
                        (Boolean) bs.get("textBold"), (Boolean) bs.get("textItalic"), (Boolean) bs.get("textUnderline"));
            }
        } else {
            List<Map<String, Object>> sceneSnapshots = (List<Map<String, Object>>) snapshot.get("scenes");
            if (sceneSnapshots != null) {
                int order = 1;
                for (Map<String, Object> ss : sceneSnapshots) {
                    restoreBlock(project, order++, (String) ss.get("name"), Block.TYPE_SCENE, null, originalIdToNewPerson, false, false, null, false, null, false, false, false);
                    List<Map<String, Object>> sceneBlocks = (List<Map<String, Object>>) ss.get("blocks");
                    if (sceneBlocks != null) {
                        for (Map<String, Object> bs : sceneBlocks) {
                            restoreBlock(project, order++, (String) bs.get("content"), Block.TYPE_ACTION,
                                    (Integer) bs.get("personOriginalId"), originalIdToNewPerson,
                                    (Boolean) bs.get("bookmarked"), (Boolean) bs.get("pinned"), (String) bs.get("tags"),
                                    false, null, false, false, false);
                        }
                    }
                }
            }
        }
    }

    private static final int AUTO_SAVE_INTERVAL_MINUTES = 10;

    @Override
    @Transactional
    public void autoSaveVersion(Integer projectId) {
        ProjectVersion latest = projectVersionRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
        if (latest != null && latest.getCreatedAt().plusMinutes(AUTO_SAVE_INTERVAL_MINUTES).isAfter(LocalDateTime.now())) {
            return;
        }
        String label = "Auto-save " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
        createVersion(projectId, label);
    }

    @Override
    @Transactional
    public void autoSaveVersionForBlock(Integer blockId) {
        Block block = blockRepository.findById(blockId).orElse(null);
        if (block != null) {
            autoSaveVersion(block.getProject().getId());
        }
    }

    @Override
    @Transactional
    public void autoSaveVersionForPerson(Integer personId) {
        Person person = personRepository.findById(personId).orElse(null);
        if (person != null) {
            autoSaveVersion(person.getProject().getId());
        }
    }

    @Override
    @Transactional
    public void restoreVersion(Integer versionId) {
        ProjectVersion version = projectVersionRepository.findById(versionId).orElse(null);
        Map<String, Object> snapshot;
        try {
            snapshot = objectMapper.readValue(version.getSnapshotJson(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize project snapshot", e);
        }
        applySnapshot(version.getProject().getId(), snapshot);
    }

    private void restoreBlock(Project project, Integer order, String content, String type,
                              Integer personOriginalId, Map<Integer, Person> originalIdToNewPerson,
                              Boolean bookmarked, Boolean pinned, String tags, Boolean sceneDelimiter,
                              String textAlign, Boolean textBold, Boolean textItalic, Boolean textUnderline) {
        Block block = new Block();
        block.setOrder(order);
        block.setContent(content != null ? content : "");
        String normalizedType = type != null && Block.ELEMENT_TYPES.contains(type) ? type : Block.TYPE_ACTION;
        block.setType(normalizedType);
        boolean delimiter = Boolean.TRUE.equals(sceneDelimiter);
        if (sceneDelimiter == null && Block.TYPE_SCENE.equals(normalizedType)) {
            delimiter = false;
        }
        block.setSceneDelimiter(delimiter);
        block.setBookmarked(Boolean.TRUE.equals(bookmarked));
        block.setPinned(Boolean.TRUE.equals(pinned));
        block.setTags(tags);
        if (textAlign != null && Block.TEXT_ALIGNS.contains(textAlign.toUpperCase())) {
            block.setTextAlign(textAlign.toUpperCase());
        }
        block.setTextBold(Boolean.TRUE.equals(textBold));
        block.setTextItalic(Boolean.TRUE.equals(textItalic));
        block.setTextUnderline(Boolean.TRUE.equals(textUnderline));
        block.setProject(project);
        if (personOriginalId != null) {
            Person restoredPerson = originalIdToNewPerson.get(personOriginalId);
            if (restoredPerson != null) {
                block.setPerson(restoredPerson);
            }
        }
        blockRepository.save(block);
    }

    @Override
    @Transactional
    public void deleteVersion(Integer versionId) {
        projectVersionRepository.deleteById(versionId);
    }
}
