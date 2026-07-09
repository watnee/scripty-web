package com.scripty.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.Actor;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.ProjectVersion;
import com.scripty.repository.ActorRepository;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ProjectVersionRepository;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.project.versionhistory.VersionChangeSummary;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;
import com.scripty.viewmodel.project.versionhistory.VersionViewModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectVersionServiceImpl implements ProjectVersionService {

    private static final int AUTO_SAVE_INTERVAL_MINUTES = 10;
    private static final DateTimeFormatter AUTO_SAVE_LABEL_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final ProjectRepository projectRepository;
    private final ProjectVersionRepository projectVersionRepository;
    private final BlockRepository blockRepository;
    private final PersonRepository personRepository;
    private final ActorRepository actorRepository;
    private final ObjectMapper objectMapper;
    private final ProjectActivityService projectActivityService;

    @Autowired
    public ProjectVersionServiceImpl(ProjectRepository projectRepository,
                                     ProjectVersionRepository projectVersionRepository,
                                     BlockRepository blockRepository,
                                     PersonRepository personRepository,
                                     ActorRepository actorRepository,
                                     ObjectMapper objectMapper,
                                     ProjectActivityService projectActivityService) {
        this.projectRepository = projectRepository;
        this.projectVersionRepository = projectVersionRepository;
        this.blockRepository = blockRepository;
        this.personRepository = personRepository;
        this.actorRepository = actorRepository;
        this.objectMapper = objectMapper;
        this.projectActivityService = projectActivityService;
    }

    @Override
    public VersionHistoryViewModel getVersionHistoryViewModel(Integer projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            VersionHistoryViewModel empty = new VersionHistoryViewModel();
            empty.setProjectId(projectId != null ? projectId : 0);
            empty.setProjectTitle("");
            empty.setVersions(List.of());
            return empty;
        }

        List<ProjectVersion> versions = projectVersionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        VersionHistoryViewModel vm = new VersionHistoryViewModel();
        vm.setProjectId(project.getId());
        vm.setProjectTitle(project.getTitle());

        List<VersionViewModel> versionVMs = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            ProjectVersion version = versions.get(i);
            VersionViewModel vvm = new VersionViewModel();
            vvm.setId(version.getId());
            vvm.setLabel(version.getLabel());
            vvm.setCreatedAt(version.getCreatedAt());
            vvm.setAutoSave(isAutoSaveLabel(version.getLabel()));
            try {
                Map<String, Object> snapshot = objectMapper.readValue(version.getSnapshotJson(), Map.class);
                populateCounts(vvm, snapshot);
                Map<String, Object> olderSnapshot = null;
                if (i + 1 < versions.size()) {
                    olderSnapshot = objectMapper.readValue(versions.get(i + 1).getSnapshotJson(), Map.class);
                }
                vvm.setChangeSummary(computeChangeSummary(snapshot, olderSnapshot));
            } catch (JsonProcessingException e) {
                vvm.setSceneCount(0);
                vvm.setBlockCount(0);
                vvm.setCharacterCount(0);
                VersionChangeSummary emptySummary = new VersionChangeSummary();
                emptySummary.addDetail("Unable to read snapshot");
                vvm.setChangeSummary(emptySummary);
            }
            versionVMs.add(vvm);
        }
        vm.setVersions(versionVMs);
        return vm;
    }

    @SuppressWarnings("unchecked")
    private void populateCounts(VersionViewModel vvm, Map<String, Object> snapshot) {
        List<?> persons = (List<?>) snapshot.get("persons");
        vvm.setCharacterCount(persons != null ? persons.size() : 0);

        List<?> blocks = (List<?>) snapshot.get("blocks");
        if (blocks != null) {
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
            return;
        }

        List<?> scenes = (List<?>) snapshot.get("scenes");
        vvm.setSceneCount(scenes != null ? scenes.size() : 0);
        int blockCount = 0;
        if (scenes != null) {
            for (Object s : scenes) {
                Map<?, ?> sceneMap = (Map<?, ?>) s;
                List<?> sceneBlocks = (List<?>) sceneMap.get("blocks");
                if (sceneBlocks != null) {
                    blockCount += sceneBlocks.size();
                }
            }
        }
        vvm.setBlockCount(blockCount);
    }

    @Override
    @Transactional
    public ProjectVersion createVersion(Integer projectId, String label) {
        ProjectVersion version = createVersionFromSnapshot(projectId, label, buildSnapshotJson(projectId));
        if (version != null && label != null && !isAutoSaveLabel(label)) {
            projectActivityService.recordForCurrentUser(
                    projectId,
                    ProjectActivity.ACTION_VERSION_SAVED,
                    "saved a version" + (label.isBlank() ? "" : " (\"" + label + "\")"),
                    ProjectActivity.ENTITY_VERSION,
                    version.getId());
        }
        return version;
    }

    private ProjectVersion createVersionFromSnapshot(Integer projectId, String label, String snapshotJson) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return null;
        }
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
        if (project == null) {
            return;
        }

        List<Block> existingBlocks = blockRepository.findByProjectIdOrderByOrderAsc(projectId);
        blockRepository.deleteAll(existingBlocks);

        List<Person> existingPersons = personRepository.findByProjectIdOrderByNameAsc(projectId);
        personRepository.deleteAll(existingPersons);

        project.setTitle(PlainTextSanitizer.sanitizeSingleLine((String) snapshot.get("title")));
        if (snapshot.containsKey("screenplayTitle")) {
            project.setScreenplayTitle(PlainTextSanitizer.sanitizeSingleLine((String) snapshot.get("screenplayTitle")));
        }
        if (snapshot.containsKey("writers")) {
            project.setWriters(PlainTextSanitizer.sanitizeSingleLine((String) snapshot.get("writers")));
        }
        if (snapshot.containsKey("contactInfo")) {
            project.setContactInfo(PlainTextSanitizer.sanitize((String) snapshot.get("contactInfo")));
        }
        projectRepository.save(project);

        List<Map<String, Object>> personSnapshots = (List<Map<String, Object>>) snapshot.get("persons");
        Map<Integer, Person> originalIdToNewPerson = new HashMap<>();
        if (personSnapshots != null) {
            for (Map<String, Object> ps : personSnapshots) {
                Person person = new Person();
                person.setName(PlainTextSanitizer.sanitizeSingleLine((String) ps.get("name")));
                person.setFullName(PlainTextSanitizer.sanitizeSingleLine((String) ps.get("fullName")));
                person.setProject(project);
                Integer actorId = toInteger(ps.get("actorId"));
                if (actorId != null) {
                    Actor actor = actorRepository.findById(actorId).orElse(null);
                    if (actor != null) {
                        person.setActor(actor);
                    }
                }
                person = personRepository.save(person);
                Integer originalId = toInteger(ps.get("originalId"));
                if (originalId != null) {
                    originalIdToNewPerson.put(originalId, person);
                }
            }
        }

        List<Map<String, Object>> blockSnapshots = (List<Map<String, Object>>) snapshot.get("blocks");
        if (blockSnapshots != null) {
            for (Map<String, Object> bs : blockSnapshots) {
                restoreBlock(project, toInteger(bs.get("order")), (String) bs.get("content"),
                        (String) bs.get("type"), toInteger(bs.get("personOriginalId")), originalIdToNewPerson,
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
                                    toInteger(bs.get("personOriginalId")), originalIdToNewPerson,
                                    (Boolean) bs.get("bookmarked"), (Boolean) bs.get("pinned"), (String) bs.get("tags"),
                                    false, null, false, false, false);
                        }
                    }
                }
            }
        }
    }

    @Override
    @Transactional
    public void autoSaveVersion(Integer projectId) {
        ProjectVersion latest = projectVersionRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
        String snapshotJson = buildSnapshotJson(projectId);
        if (latest != null && snapshotJson.equals(latest.getSnapshotJson())) {
            return;
        }
        if (latest != null
                && isAutoSaveLabel(latest.getLabel())
                && latest.getCreatedAt().plusMinutes(AUTO_SAVE_INTERVAL_MINUTES).isAfter(LocalDateTime.now())) {
            latest.setSnapshotJson(snapshotJson);
            latest.setCreatedAt(LocalDateTime.now());
            latest.setLabel("Auto-save " + LocalDateTime.now().format(AUTO_SAVE_LABEL_FORMAT));
            projectVersionRepository.save(latest);
            return;
        }
        String label = "Auto-save " + LocalDateTime.now().format(AUTO_SAVE_LABEL_FORMAT);
        createVersionFromSnapshot(projectId, label, snapshotJson);
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
        if (version == null || version.getProject() == null) {
            return;
        }
        Integer projectId = version.getProject().getId();
        Map<String, Object> snapshot;
        try {
            snapshot = objectMapper.readValue(version.getSnapshotJson(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize project snapshot", e);
        }

        String beforeLabel = "Before restore " + LocalDateTime.now().format(AUTO_SAVE_LABEL_FORMAT);
        createVersionFromSnapshot(projectId, beforeLabel, buildSnapshotJson(projectId));

        applySnapshot(projectId, snapshot);
        projectActivityService.recordForCurrentUser(
                projectId,
                ProjectActivity.ACTION_VERSION_RESTORED,
                "restored a previous version",
                ProjectActivity.ENTITY_VERSION,
                versionId);
    }

    @Override
    @Transactional
    public boolean restoreVersionForProject(Integer versionId, Integer projectId) {
        ProjectVersion version = projectVersionRepository.findById(versionId).orElse(null);
        if (version == null || version.getProject() == null || projectId == null
                || !projectId.equals(version.getProject().getId())) {
            return false;
        }
        restoreVersion(versionId);
        return true;
    }

    private void restoreBlock(Project project, Integer order, String content, String type,
                              Integer personOriginalId, Map<Integer, Person> originalIdToNewPerson,
                              Boolean bookmarked, Boolean pinned, String tags, Boolean sceneDelimiter,
                              String textAlign, Boolean textBold, Boolean textItalic, Boolean textUnderline) {
        Block block = new Block();
        block.setOrder(order);
        block.setContent(PlainTextSanitizer.sanitize(content != null ? content : ""));
        String normalizedType = type != null && Block.ELEMENT_TYPES.contains(type) ? type : Block.TYPE_ACTION;
        block.setType(normalizedType);
        boolean delimiter = Boolean.TRUE.equals(sceneDelimiter);
        if (sceneDelimiter == null && Block.TYPE_SCENE.equals(normalizedType)) {
            delimiter = false;
        }
        block.setSceneDelimiter(delimiter);
        block.setBookmarked(Boolean.TRUE.equals(bookmarked));
        block.setPinned(Boolean.TRUE.equals(pinned));
        block.setTags(PlainTextSanitizer.sanitizeSingleLine(tags));
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

    @Override
    @Transactional
    public boolean deleteVersionForProject(Integer versionId, Integer projectId) {
        ProjectVersion version = projectVersionRepository.findById(versionId).orElse(null);
        if (version == null || version.getProject() == null || projectId == null
                || !projectId.equals(version.getProject().getId())) {
            return false;
        }
        projectVersionRepository.deleteById(versionId);
        return true;
    }

    @SuppressWarnings("unchecked")
    private VersionChangeSummary computeChangeSummary(Map<String, Object> newer, Map<String, Object> older) {
        VersionChangeSummary summary = new VersionChangeSummary();
        if (older == null) {
            summary.addDetail("Initial saved state");
            return summary;
        }

        if (!Objects.equals(newer.get("title"), older.get("title"))
                || !Objects.equals(newer.get("screenplayTitle"), older.get("screenplayTitle"))
                || !Objects.equals(newer.get("writers"), older.get("writers"))
                || !Objects.equals(newer.get("contactInfo"), older.get("contactInfo"))) {
            summary.setProjectMetadataChanged(true);
            summary.addDetail("Project details changed");
        }

        List<Map<String, Object>> newerBlocks = (List<Map<String, Object>>) newer.get("blocks");
        List<Map<String, Object>> olderBlocks = (List<Map<String, Object>>) older.get("blocks");
        if (newerBlocks != null || olderBlocks != null) {
            diffFlatBlocks(summary, newerBlocks, olderBlocks);
        } else {
            diffLegacyScenes(summary,
                    (List<Map<String, Object>>) newer.get("scenes"),
                    (List<Map<String, Object>>) older.get("scenes"));
        }
        diffPersons(summary,
                (List<Map<String, Object>>) newer.get("persons"),
                (List<Map<String, Object>>) older.get("persons"));
        return summary;
    }

    private void diffFlatBlocks(VersionChangeSummary summary,
                                List<Map<String, Object>> newerBlocks,
                                List<Map<String, Object>> olderBlocks) {
        Map<Integer, Map<String, Object>> newerByOrder = indexBlocksByOrder(newerBlocks);
        Map<Integer, Map<String, Object>> olderByOrder = indexBlocksByOrder(olderBlocks);

        for (Map.Entry<Integer, Map<String, Object>> entry : newerByOrder.entrySet()) {
            Map<String, Object> olderBlock = olderByOrder.get(entry.getKey());
            Map<String, Object> newerBlock = entry.getValue();
            if (olderBlock == null) {
                if (isSceneBlock(newerBlock)) {
                    summary.setScenesAdded(summary.getScenesAdded() + 1);
                    summary.addDetail("Scene added: " + sceneBlockLabel(newerBlock));
                } else {
                    summary.setBlocksAdded(summary.getBlocksAdded() + 1);
                    summary.addDetail("Block added: " + blockPreview(newerBlock));
                }
                continue;
            }
            if (isSceneBlock(newerBlock) && isSceneBlock(olderBlock)) {
                if (!Objects.equals(newerBlock.get("content"), olderBlock.get("content"))) {
                    summary.setScenesRenamed(summary.getScenesRenamed() + 1);
                    summary.addDetail("Scene renamed: " + sceneBlockLabel(olderBlock)
                            + " → " + sceneBlockLabel(newerBlock));
                } else if (blockChanged(newerBlock, olderBlock)) {
                    summary.setBlocksEdited(summary.getBlocksEdited() + 1);
                    summary.addDetail("Scene updated: " + sceneBlockLabel(newerBlock));
                }
            } else if (blockChanged(newerBlock, olderBlock) || !Objects.equals(newerBlock.get("type"), olderBlock.get("type"))) {
                if (isSceneBlock(newerBlock) && !isSceneBlock(olderBlock)) {
                    summary.setScenesAdded(summary.getScenesAdded() + 1);
                    summary.setBlocksRemoved(summary.getBlocksRemoved() + 1);
                    summary.addDetail("Became scene: " + sceneBlockLabel(newerBlock));
                } else if (!isSceneBlock(newerBlock) && isSceneBlock(olderBlock)) {
                    summary.setScenesRemoved(summary.getScenesRemoved() + 1);
                    summary.setBlocksAdded(summary.getBlocksAdded() + 1);
                    summary.addDetail("Scene became block: " + blockPreview(newerBlock));
                } else {
                    summary.setBlocksEdited(summary.getBlocksEdited() + 1);
                    summary.addDetail("Block edited: " + blockPreview(newerBlock));
                }
            }
        }

        for (Map.Entry<Integer, Map<String, Object>> entry : olderByOrder.entrySet()) {
            if (!newerByOrder.containsKey(entry.getKey())) {
                if (isSceneBlock(entry.getValue())) {
                    summary.setScenesRemoved(summary.getScenesRemoved() + 1);
                    summary.addDetail("Scene removed: " + sceneBlockLabel(entry.getValue()));
                } else {
                    summary.setBlocksRemoved(summary.getBlocksRemoved() + 1);
                    summary.addDetail("Block removed: " + blockPreview(entry.getValue()));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void diffLegacyScenes(VersionChangeSummary summary,
                                  List<Map<String, Object>> newerScenes,
                                  List<Map<String, Object>> olderScenes) {
        Map<Integer, Map<String, Object>> newerByOrder = indexByOrder(newerScenes);
        Map<Integer, Map<String, Object>> olderByOrder = indexByOrder(olderScenes);

        for (Map.Entry<Integer, Map<String, Object>> entry : newerByOrder.entrySet()) {
            Map<String, Object> olderScene = olderByOrder.get(entry.getKey());
            if (olderScene == null) {
                summary.setScenesAdded(summary.getScenesAdded() + 1);
                summary.addDetail("Scene added: " + legacySceneLabel(entry.getValue()));
                continue;
            }
            String sceneName = legacySceneLabel(entry.getValue());
            if (!Objects.equals(entry.getValue().get("name"), olderScene.get("name"))) {
                summary.setScenesRenamed(summary.getScenesRenamed() + 1);
                summary.addDetail("Scene renamed: " + olderScene.get("name") + " → " + entry.getValue().get("name"));
            }
            diffBlocksInLegacyScene(summary, entry.getValue(), olderScene, sceneName);
        }

        for (Map.Entry<Integer, Map<String, Object>> entry : olderByOrder.entrySet()) {
            if (!newerByOrder.containsKey(entry.getKey())) {
                summary.setScenesRemoved(summary.getScenesRemoved() + 1);
                summary.addDetail("Scene removed: " + legacySceneLabel(entry.getValue()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void diffBlocksInLegacyScene(VersionChangeSummary summary,
                                         Map<String, Object> newerScene,
                                         Map<String, Object> olderScene,
                                         String sceneName) {
        Map<Integer, Map<String, Object>> newerBlocks =
                indexBlocksByOrder((List<Map<String, Object>>) newerScene.get("blocks"));
        Map<Integer, Map<String, Object>> olderBlocks =
                indexBlocksByOrder((List<Map<String, Object>>) olderScene.get("blocks"));

        for (Map.Entry<Integer, Map<String, Object>> entry : newerBlocks.entrySet()) {
            Map<String, Object> olderBlock = olderBlocks.get(entry.getKey());
            if (olderBlock == null) {
                summary.setBlocksAdded(summary.getBlocksAdded() + 1);
                summary.addDetail("Block added in " + sceneName + ": " + blockPreview(entry.getValue()));
                continue;
            }
            if (blockChanged(entry.getValue(), olderBlock)) {
                summary.setBlocksEdited(summary.getBlocksEdited() + 1);
                summary.addDetail("Block edited in " + sceneName + ": " + blockPreview(entry.getValue()));
            }
        }

        for (Map.Entry<Integer, Map<String, Object>> entry : olderBlocks.entrySet()) {
            if (!newerBlocks.containsKey(entry.getKey())) {
                summary.setBlocksRemoved(summary.getBlocksRemoved() + 1);
                summary.addDetail("Block removed from " + sceneName + ": " + blockPreview(entry.getValue()));
            }
        }
    }

    private void diffPersons(VersionChangeSummary summary,
                             List<Map<String, Object>> newerPersons,
                             List<Map<String, Object>> olderPersons) {
        Map<Integer, Map<String, Object>> newerById = indexPersonsByOriginalId(newerPersons);
        Map<Integer, Map<String, Object>> olderById = indexPersonsByOriginalId(olderPersons);

        for (Map.Entry<Integer, Map<String, Object>> entry : newerById.entrySet()) {
            if (!olderById.containsKey(entry.getKey())) {
                summary.setCharactersAdded(summary.getCharactersAdded() + 1);
                summary.addDetail("Character added: " + personLabel(entry.getValue()));
            }
        }

        for (Map.Entry<Integer, Map<String, Object>> entry : olderById.entrySet()) {
            if (!newerById.containsKey(entry.getKey())) {
                summary.setCharactersRemoved(summary.getCharactersRemoved() + 1);
                summary.addDetail("Character removed: " + personLabel(entry.getValue()));
            }
        }
    }

    private Map<Integer, Map<String, Object>> indexByOrder(List<Map<String, Object>> items) {
        Map<Integer, Map<String, Object>> indexed = new HashMap<>();
        if (items == null) {
            return indexed;
        }
        for (Map<String, Object> item : items) {
            Integer order = toInteger(item.get("order"));
            if (order != null) {
                indexed.put(order, item);
            }
        }
        return indexed;
    }

    private Map<Integer, Map<String, Object>> indexBlocksByOrder(List<Map<String, Object>> blocks) {
        return indexByOrder(blocks);
    }

    private Map<Integer, Map<String, Object>> indexPersonsByOriginalId(List<Map<String, Object>> persons) {
        Map<Integer, Map<String, Object>> indexed = new HashMap<>();
        if (persons == null) {
            return indexed;
        }
        for (Map<String, Object> person : persons) {
            Integer originalId = toInteger(person.get("originalId"));
            if (originalId != null) {
                indexed.put(originalId, person);
            }
        }
        return indexed;
    }

    private boolean blockChanged(Map<String, Object> newer, Map<String, Object> older) {
        return !Objects.equals(newer.get("content"), older.get("content"))
                || !Objects.equals(newer.get("type"), older.get("type"))
                || !Objects.equals(toInteger(newer.get("personOriginalId")), toInteger(older.get("personOriginalId")))
                || !Objects.equals(newer.get("tags"), older.get("tags"))
                || !Objects.equals(newer.get("textAlign"), older.get("textAlign"))
                || toBoolean(newer.get("bookmarked")) != toBoolean(older.get("bookmarked"))
                || toBoolean(newer.get("pinned")) != toBoolean(older.get("pinned"))
                || toBoolean(newer.get("textBold")) != toBoolean(older.get("textBold"))
                || toBoolean(newer.get("textItalic")) != toBoolean(older.get("textItalic"))
                || toBoolean(newer.get("textUnderline")) != toBoolean(older.get("textUnderline"))
                || toBoolean(newer.get("sceneDelimiter")) != toBoolean(older.get("sceneDelimiter"));
    }

    private static boolean isSceneBlock(Map<String, Object> block) {
        return Block.TYPE_SCENE.equals(block.get("type"));
    }

    private static boolean isAutoSaveLabel(String label) {
        return label != null && label.startsWith("Auto-save");
    }

    private String sceneBlockLabel(Map<String, Object> block) {
        Object content = block.get("content");
        if (content != null && !content.toString().isBlank()) {
            return content.toString();
        }
        Integer order = toInteger(block.get("order"));
        return order != null ? "Scene " + order : "Scene";
    }

    private String legacySceneLabel(Map<String, Object> scene) {
        Object name = scene.get("name");
        if (name != null && !name.toString().isBlank()) {
            return name.toString();
        }
        Integer order = toInteger(scene.get("order"));
        return order != null ? "Scene " + order : "Scene";
    }

    private String personLabel(Map<String, Object> person) {
        Object name = person.get("name");
        if (name != null && !name.toString().isBlank()) {
            return name.toString();
        }
        return "Character";
    }

    private String blockPreview(Map<String, Object> block) {
        Object type = block.get("type");
        String typePrefix = type != null ? Block.typeLabelFor(type.toString()) + " " : "";
        Object content = block.get("content");
        if (content == null || content.toString().isBlank()) {
            return typePrefix + "(empty)";
        }
        String text = content.toString().replaceAll("\\s+", " ").trim();
        if (text.length() <= 48) {
            return typePrefix + "\"" + text + "\"";
        }
        return typePrefix + "\"" + text.substring(0, 45) + "...\"";
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static boolean toBoolean(Object value) {
        return value instanceof Boolean bool && bool;
    }
}
