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
        for (ProjectVersion version : versions) {
            VersionViewModel vvm = new VersionViewModel();
            vvm.setId(version.getId());
            vvm.setLabel(version.getLabel());
            vvm.setCreatedAt(version.getCreatedAt());
            try {
                Map<String, Object> snapshot = objectMapper.readValue(version.getSnapshotJson(), Map.class);
                List<?> scenes = (List<?>) snapshot.get("scenes");
                List<?> persons = (List<?>) snapshot.get("persons");
                vvm.setSceneCount(scenes != null ? scenes.size() : 0);
                vvm.setCharacterCount(persons != null ? persons.size() : 0);
                int blockCount = 0;
                if (scenes != null) {
                    for (Object s : scenes) {
                        Map<?, ?> sceneMap = (Map<?, ?>) s;
                        List<?> blocks = (List<?>) sceneMap.get("blocks");
                        if (blocks != null) blockCount += blocks.size();
                    }
                }
                vvm.setBlockCount(blockCount);
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
        Project project = projectRepository.findById(projectId).orElse(null);
        String snapshotJson = buildSnapshotJson(projectId);

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
        if (latest != null && latest.getCreatedAt().plusMinutes(AUTO_SAVE_INTERVAL_MINUTES).isAfter(LocalDateTime.now())) {
            return;
        }
        String label = "Auto-save " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
        createVersion(projectId, label);
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
}
