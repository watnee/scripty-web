package com.scripty.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectVersion;
import com.scripty.dto.Scene;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ProjectVersionRepository;
import com.scripty.repository.SceneRepository;
import com.scripty.viewmodel.project.versionhistory.VersionChangeSummary;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;
import com.scripty.viewmodel.project.versionhistory.VersionViewModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    private final ProjectRepository projectRepository;
    private final ProjectVersionRepository projectVersionRepository;
    private final SceneRepository sceneRepository;
    private final BlockRepository blockRepository;
    private final PersonRepository personRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public ProjectVersionServiceImpl(ProjectRepository projectRepository,
                                     ProjectVersionRepository projectVersionRepository,
                                     SceneRepository sceneRepository,
                                     BlockRepository blockRepository,
                                     PersonRepository personRepository,
                                     ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.projectVersionRepository = projectVersionRepository;
        this.sceneRepository = sceneRepository;
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
        for (int i = 0; i < versions.size(); i++) {
            ProjectVersion version = versions.get(i);
            VersionViewModel vvm = new VersionViewModel();
            vvm.setId(version.getId());
            vvm.setLabel(version.getLabel());
            vvm.setCreatedAt(version.getCreatedAt());
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
        List<?> scenes = (List<?>) snapshot.get("scenes");
        List<?> persons = (List<?>) snapshot.get("persons");
        vvm.setSceneCount(scenes != null ? scenes.size() : 0);
        vvm.setCharacterCount(persons != null ? persons.size() : 0);
        int blockCount = 0;
        if (scenes != null) {
            for (Object s : scenes) {
                Map<?, ?> sceneMap = (Map<?, ?>) s;
                List<?> blocks = (List<?>) sceneMap.get("blocks");
                if (blocks != null) {
                    blockCount += blocks.size();
                }
            }
        }
        vvm.setBlockCount(blockCount);
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
        Map<String, Object> snapshot = buildSnapshot(projectId);
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
    private Map<String, Object> buildSnapshot(Integer projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        List<Scene> scenes = sceneRepository.findByProjectIdOrderByOrderAsc(projectId);
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

        List<Map<String, Object>> sceneSnapshots = new ArrayList<>();
        for (Scene scene : scenes) {
            Map<String, Object> s = new HashMap<>();
            s.put("order", scene.getOrder());
            s.put("name", scene.getName());

            List<Block> blocks = blockRepository.findBySceneIdOrderByOrderAsc(scene.getId());
            List<Map<String, Object>> blockSnapshots = new ArrayList<>();
            for (Block block : blocks) {
                Map<String, Object> b = new HashMap<>();
                b.put("order", block.getOrder());
                b.put("content", block.getContent());
                b.put("bookmarked", block.isBookmarked());
                b.put("pinned", block.isPinned());
                b.put("tags", block.getTags());
                b.put("personOriginalId", block.getPerson() != null ? block.getPerson().getId() : null);
                blockSnapshots.add(b);
            }
            s.put("blocks", blockSnapshots);
            sceneSnapshots.add(s);
        }
        snapshot.put("scenes", sceneSnapshots);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private void applySnapshot(Integer projectId, Map<String, Object> snapshot) {
        Project project = projectRepository.findById(projectId).orElse(null);

        List<Scene> existingScenes = sceneRepository.findByProjectIdOrderByOrderAsc(projectId);
        for (Scene scene : existingScenes) {
            List<Block> blocks = blockRepository.findBySceneIdOrderByOrderAsc(scene.getId());
            blockRepository.deleteAll(blocks);
        }
        sceneRepository.deleteAll(existingScenes);

        List<Person> existingPersons = personRepository.findByProjectIdOrderByNameAsc(projectId);
        personRepository.deleteAll(existingPersons);

        entityManager.flush();
        entityManager.clear();
        project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }

        project.setTitle((String) snapshot.get("title"));
        project.setScreenplayTitle((String) snapshot.get("screenplayTitle"));
        project.setWriters((String) snapshot.get("writers"));
        project.setContactInfo((String) snapshot.get("contactInfo"));
        project.setLastEdited(LocalDateTime.now());
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
                Integer originalId = toInteger(ps.get("originalId"));
                if (originalId != null) {
                    originalIdToNewPerson.put(originalId, person);
                }
            }
        }

        List<Map<String, Object>> sceneSnapshots = (List<Map<String, Object>>) snapshot.get("scenes");
        if (sceneSnapshots != null) {
            for (Map<String, Object> ss : sceneSnapshots) {
                Scene scene = new Scene();
                scene.setOrder(toInteger(ss.get("order")));
                scene.setName((String) ss.get("name"));
                scene.setProject(project);
                scene = sceneRepository.save(scene);

                List<Map<String, Object>> blockSnapshots = (List<Map<String, Object>>) ss.get("blocks");
                if (blockSnapshots != null) {
                    for (Map<String, Object> bs : blockSnapshots) {
                        Block block = new Block();
                        block.setOrder(toInteger(bs.get("order")));
                        String content = (String) bs.get("content");
                        block.setContent(content != null ? content : "");
                        block.setScene(scene);
                        block.setBookmarked(toBoolean(bs.get("bookmarked")));
                        block.setPinned(toBoolean(bs.get("pinned")));
                        block.setTags((String) bs.get("tags"));
                        Integer personOriginalId = toInteger(bs.get("personOriginalId"));
                        if (personOriginalId != null) {
                            Person restoredPerson = originalIdToNewPerson.get(personOriginalId);
                            if (restoredPerson != null) {
                                block.setPerson(restoredPerson);
                            }
                        }
                        blockRepository.save(block);
                    }
                }
            }
        }
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

    private static final int AUTO_SAVE_INTERVAL_MINUTES = 10;

    @Override
    @Transactional
    public void autoSaveVersion(Integer projectId) {
        ProjectVersion latest = projectVersionRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
        String snapshotJson = buildSnapshotJson(projectId);
        if (latest != null && snapshotJson.equals(latest.getSnapshotJson())) {
            return;
        }
        if (latest != null
                && latest.getLabel() != null
                && latest.getLabel().startsWith("Auto-save")
                && latest.getCreatedAt().plusMinutes(AUTO_SAVE_INTERVAL_MINUTES).isAfter(LocalDateTime.now())) {
            latest.setSnapshotJson(snapshotJson);
            latest.setCreatedAt(LocalDateTime.now());
            projectVersionRepository.save(latest);
            return;
        }
        String label = "Auto-save " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
        createVersionFromSnapshot(projectId, label, snapshotJson);
    }

    @Override
    @Transactional
    public void autoSaveVersionForScene(Integer sceneId) {
        Scene scene = sceneRepository.findById(sceneId).orElse(null);
        if (scene != null) {
            autoSaveVersion(scene.getProject().getId());
        }
    }

    @Override
    @Transactional
    public void autoSaveVersionForBlock(Integer blockId) {
        Block block = blockRepository.findById(blockId).orElse(null);
        if (block != null) {
            autoSaveVersion(block.getScene().getProject().getId());
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
    @SuppressWarnings("unchecked")
    public void restoreVersion(Integer versionId) {
        ProjectVersion version = projectVersionRepository.findById(versionId).orElse(null);
        Project project = version.getProject();
        Integer projectId = project.getId();

        Map<String, Object> snapshot;
        try {
            snapshot = objectMapper.readValue(version.getSnapshotJson(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize project snapshot", e);
        }

        applySnapshot(projectId, snapshot);
    }

    @Override
    @Transactional
    public void deleteVersion(Integer versionId) {
        projectVersionRepository.deleteById(versionId);
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

        diffScenes(summary, (List<Map<String, Object>>) newer.get("scenes"), (List<Map<String, Object>>) older.get("scenes"));
        diffPersons(summary, (List<Map<String, Object>>) newer.get("persons"), (List<Map<String, Object>>) older.get("persons"));
        return summary;
    }

    private void diffScenes(VersionChangeSummary summary,
                            List<Map<String, Object>> newerScenes,
                            List<Map<String, Object>> olderScenes) {
        Map<Integer, Map<String, Object>> newerByOrder = indexScenesByOrder(newerScenes);
        Map<Integer, Map<String, Object>> olderByOrder = indexScenesByOrder(olderScenes);

        for (Map.Entry<Integer, Map<String, Object>> entry : newerByOrder.entrySet()) {
            Map<String, Object> olderScene = olderByOrder.get(entry.getKey());
            if (olderScene == null) {
                summary.setScenesAdded(summary.getScenesAdded() + 1);
                summary.addDetail("Scene added: " + sceneLabel(entry.getValue()));
                continue;
            }
            String sceneName = sceneLabel(entry.getValue());
            if (!Objects.equals(entry.getValue().get("name"), olderScene.get("name"))) {
                summary.setScenesRenamed(summary.getScenesRenamed() + 1);
                summary.addDetail("Scene renamed: " + olderScene.get("name") + " → " + entry.getValue().get("name"));
            }
            diffBlocksInScene(summary, entry.getValue(), olderScene, sceneName);
        }

        for (Map.Entry<Integer, Map<String, Object>> entry : olderByOrder.entrySet()) {
            if (!newerByOrder.containsKey(entry.getKey())) {
                summary.setScenesRemoved(summary.getScenesRemoved() + 1);
                summary.addDetail("Scene removed: " + sceneLabel(entry.getValue()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void diffBlocksInScene(VersionChangeSummary summary,
                                   Map<String, Object> newerScene,
                                   Map<String, Object> olderScene,
                                   String sceneName) {
        Map<Integer, Map<String, Object>> newerBlocks = indexBlocksByOrder((List<Map<String, Object>>) newerScene.get("blocks"));
        Map<Integer, Map<String, Object>> olderBlocks = indexBlocksByOrder((List<Map<String, Object>>) olderScene.get("blocks"));

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

    private Map<Integer, Map<String, Object>> indexScenesByOrder(List<Map<String, Object>> scenes) {
        Map<Integer, Map<String, Object>> indexed = new HashMap<>();
        if (scenes == null) {
            return indexed;
        }
        for (Map<String, Object> scene : scenes) {
            Integer order = toInteger(scene.get("order"));
            if (order != null) {
                indexed.put(order, scene);
            }
        }
        return indexed;
    }

    private Map<Integer, Map<String, Object>> indexBlocksByOrder(List<Map<String, Object>> blocks) {
        Map<Integer, Map<String, Object>> indexed = new HashMap<>();
        if (blocks == null) {
            return indexed;
        }
        for (Map<String, Object> block : blocks) {
            Integer order = toInteger(block.get("order"));
            if (order != null) {
                indexed.put(order, block);
            }
        }
        return indexed;
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
                || !Objects.equals(newer.get("personOriginalId"), older.get("personOriginalId"))
                || !Objects.equals(newer.get("tags"), older.get("tags"))
                || toBoolean(newer.get("bookmarked")) != toBoolean(older.get("bookmarked"))
                || toBoolean(newer.get("pinned")) != toBoolean(older.get("pinned"));
    }

    private String sceneLabel(Map<String, Object> scene) {
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
        Object content = block.get("content");
        if (content == null || content.toString().isBlank()) {
            return "(empty block)";
        }
        String text = content.toString().replaceAll("\\s+", " ").trim();
        if (text.length() <= 48) {
            return "\"" + text + "\"";
        }
        return "\"" + text.substring(0, 45) + "...\"";
    }
}
