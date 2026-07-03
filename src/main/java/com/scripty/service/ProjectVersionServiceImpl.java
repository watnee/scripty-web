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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectVersionServiceImpl implements ProjectVersionService {

    private final ProjectRepository projectRepository;
    private final ProjectVersionRepository projectVersionRepository;
    private final SceneRepository sceneRepository;
    private final BlockRepository blockRepository;
    private final PersonRepository personRepository;
    private final ObjectMapper objectMapper;

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
                b.put("personOriginalId", block.getPerson() != null ? block.getPerson().getId() : null);
                blockSnapshots.add(b);
            }
            s.put("blocks", blockSnapshots);
            sceneSnapshots.add(s);
        }
        snapshot.put("scenes", sceneSnapshots);

        ProjectVersion version = new ProjectVersion();
        version.setProject(project);
        version.setLabel(label);
        version.setCreatedAt(LocalDateTime.now());
        try {
            version.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize project snapshot", e);
        }

        return projectVersionRepository.save(version);
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

        List<Scene> existingScenes = sceneRepository.findByProjectIdOrderByOrderAsc(projectId);
        for (Scene scene : existingScenes) {
            List<Block> blocks = blockRepository.findBySceneIdOrderByOrderAsc(scene.getId());
            blockRepository.deleteAll(blocks);
        }
        sceneRepository.deleteAll(existingScenes);

        List<Person> existingPersons = personRepository.findByProjectIdOrderByNameAsc(projectId);
        personRepository.deleteAll(existingPersons);

        project.setTitle((String) snapshot.get("title"));
        project.setScreenplayTitle((String) snapshot.get("screenplayTitle"));
        project.setWriters((String) snapshot.get("writers"));
        project.setContactInfo((String) snapshot.get("contactInfo"));
        project.setLastEdited(java.time.LocalDateTime.now());
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

        List<Map<String, Object>> sceneSnapshots = (List<Map<String, Object>>) snapshot.get("scenes");
        if (sceneSnapshots != null) {
            for (Map<String, Object> ss : sceneSnapshots) {
                Scene scene = new Scene();
                scene.setOrder((Integer) ss.get("order"));
                scene.setName((String) ss.get("name"));
                scene.setProject(project);
                scene = sceneRepository.save(scene);

                List<Map<String, Object>> blockSnapshots = (List<Map<String, Object>>) ss.get("blocks");
                if (blockSnapshots != null) {
                    for (Map<String, Object> bs : blockSnapshots) {
                        Block block = new Block();
                        block.setOrder((Integer) bs.get("order"));
                        block.setContent((String) bs.get("content"));
                        block.setScene(scene);
                        Integer personOriginalId = (Integer) bs.get("personOriginalId");
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

    @Override
    @Transactional
    public void deleteVersion(Integer versionId) {
        projectVersionRepository.deleteById(versionId);
    }
}
