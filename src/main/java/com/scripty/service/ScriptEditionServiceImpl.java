package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ScriptEditionRepository;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.project.edition.ScriptEditionViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScriptEditionServiceImpl implements ScriptEditionService {

    public static final String DEFAULT_EDITION_NAME = "Main";

    private final ScriptEditionRepository scriptEditionRepository;
    private final ProjectRepository projectRepository;
    private final BlockRepository blockRepository;
    private final PersonRepository personRepository;
    private final ProjectActivityService projectActivityService;

    @Autowired
    public ScriptEditionServiceImpl(ScriptEditionRepository scriptEditionRepository,
                                    ProjectRepository projectRepository,
                                    BlockRepository blockRepository,
                                    PersonRepository personRepository,
                                    ProjectActivityService projectActivityService) {
        this.scriptEditionRepository = scriptEditionRepository;
        this.projectRepository = projectRepository;
        this.blockRepository = blockRepository;
        this.personRepository = personRepository;
        this.projectActivityService = projectActivityService;
    }

    @Override
    public ScriptEdition read(Integer id) {
        if (id == null) {
            return null;
        }
        return scriptEditionRepository.findById(id).orElse(null);
    }

    @Override
    public ScriptEdition requireForProject(Integer projectId, Integer editionId) {
        if (projectId == null) {
            return null;
        }
        if (editionId != null) {
            ScriptEdition edition = scriptEditionRepository.findByIdAndProjectId(editionId, projectId).orElse(null);
            if (edition != null) {
                return edition;
            }
        }
        return getDefaultForProject(projectId);
    }

    @Override
    public ScriptEdition resolveForAccess(Integer projectId, Integer editionId, boolean canBrowseEditions) {
        if (canBrowseEditions) {
            return requireForProject(projectId, editionId);
        }
        return getPublishedForProject(projectId);
    }

    @Override
    public ScriptEdition getDefaultForProject(Integer projectId) {
        if (projectId == null) {
            return null;
        }
        return scriptEditionRepository.findDefaultByProjectId(projectId)
                .orElseGet(() -> scriptEditionRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                        .findFirst()
                        .orElse(null));
    }

    @Override
    public ScriptEdition getPublishedForProject(Integer projectId) {
        if (projectId == null) {
            return null;
        }
        return scriptEditionRepository.findPublishedByProjectId(projectId)
                .orElseGet(() -> getDefaultForProject(projectId));
    }

    @Override
    @Transactional
    public ScriptEdition ensureDefaultEdition(Integer projectId) {
        ScriptEdition existing = getDefaultForProject(projectId);
        if (existing != null) {
            if (scriptEditionRepository.findPublishedByProjectId(projectId).isEmpty()) {
                existing.setPublished(true);
                existing.setUpdatedAt(LocalDateTime.now());
                return scriptEditionRepository.save(existing);
            }
            return existing;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        ScriptEdition edition = new ScriptEdition();
        edition.setProject(project);
        edition.setName(DEFAULT_EDITION_NAME);
        edition.setDefault(true);
        edition.setPublished(true);
        edition.setCreatedAt(now);
        edition.setUpdatedAt(now);
        edition.setLastEdited(project.getLastEdited() != null ? project.getLastEdited() : now);
        return scriptEditionRepository.save(edition);
    }

    @Override
    public List<ScriptEdition> listForProject(Integer projectId) {
        if (projectId == null) {
            return List.of();
        }
        return scriptEditionRepository.findByProjectIdOrderByNameAsc(projectId);
    }

    @Override
    public List<ScriptEditionViewModel> getEditionViewModels(Integer projectId) {
        return getEditionViewModels(projectId, true);
    }

    @Override
    public List<ScriptEditionViewModel> getEditionViewModels(Integer projectId, boolean canBrowseEditions) {
        List<ScriptEditionViewModel> result = new ArrayList<>();
        List<ScriptEdition> editions = listForProject(projectId);
        if (!canBrowseEditions) {
            ScriptEdition published = getPublishedForProject(projectId);
            if (published == null) {
                return result;
            }
            editions = List.of(published);
        }
        for (ScriptEdition edition : editions) {
            ScriptEditionViewModel vm = new ScriptEditionViewModel();
            vm.setId(edition.getId());
            vm.setName(edition.getName());
            vm.setDefault(edition.isDefault());
            vm.setPublished(edition.isPublished());
            vm.setLastEdited(edition.getLastEdited());
            vm.setBlockCount(blockRepository.countByScriptEditionId(edition.getId()));
            result.add(vm);
        }
        return result;
    }

    @Override
    @Transactional
    public ScriptEdition createEdition(Integer projectId, String name, Integer copyFromEditionId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return null;
        }

        String cleanedName = PlainTextSanitizer.sanitizeSingleLine(name);
        if (cleanedName == null || cleanedName.isBlank()) {
            cleanedName = "Untitled version";
        }
        if (cleanedName.length() > 100) {
            cleanedName = cleanedName.substring(0, 100).trim();
        }
        cleanedName = uniqueName(projectId, cleanedName);

        ScriptEdition source = requireForProject(projectId, copyFromEditionId);
        if (source == null) {
            source = ensureDefaultEdition(projectId);
        }
        if (source == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        ScriptEdition edition = new ScriptEdition();
        edition.setProject(project);
        edition.setName(cleanedName);
        edition.setDefault(false);
        edition.setClonedFrom(source);
        edition.setCreatedAt(now);
        edition.setUpdatedAt(now);
        edition.setLastEdited(now);
        edition = scriptEditionRepository.save(edition);

        copyScreenplay(source, edition);

        projectActivityService.recordForCurrentUser(
                projectId,
                ProjectActivity.ACTION_SCRIPT_EDITED,
                "created screenplay version \"" + cleanedName + "\"",
                ProjectActivity.ENTITY_PROJECT,
                edition.getId());

        return edition;
    }

    @Override
    @Transactional
    public boolean renameEdition(Integer editionId, Integer projectId, String name) {
        ScriptEdition edition = scriptEditionRepository.findByIdAndProjectId(editionId, projectId).orElse(null);
        if (edition == null) {
            return false;
        }
        String cleanedName = PlainTextSanitizer.sanitizeSingleLine(name);
        if (cleanedName == null || cleanedName.isBlank()) {
            return false;
        }
        if (cleanedName.length() > 100) {
            cleanedName = cleanedName.substring(0, 100).trim();
        }
        if (!cleanedName.equalsIgnoreCase(edition.getName())
                && scriptEditionRepository.existsByProjectIdAndNameIgnoreCase(projectId, cleanedName)) {
            return false;
        }
        edition.setName(cleanedName);
        edition.setUpdatedAt(LocalDateTime.now());
        scriptEditionRepository.save(edition);
        return true;
    }

    @Override
    @Transactional
    public boolean deleteEdition(Integer editionId, Integer projectId) {
        ScriptEdition edition = scriptEditionRepository.findByIdAndProjectId(editionId, projectId).orElse(null);
        if (edition == null) {
            return false;
        }
        if (scriptEditionRepository.countByProjectId(projectId) <= 1) {
            return false;
        }
        boolean wasDefault = edition.isDefault();
        boolean wasPublished = edition.isPublished();
        scriptEditionRepository.delete(edition);
        if (wasDefault || wasPublished) {
            ScriptEdition next = scriptEditionRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                    .findFirst()
                    .orElse(null);
            if (next != null) {
                boolean changed = false;
                if (wasDefault && !next.isDefault()) {
                    next.setDefault(true);
                    changed = true;
                }
                if (wasPublished && !next.isPublished()) {
                    next.setPublished(true);
                    changed = true;
                }
                if (changed) {
                    next.setUpdatedAt(LocalDateTime.now());
                    scriptEditionRepository.save(next);
                }
            }
        }
        return true;
    }

    @Override
    @Transactional
    public boolean setDefaultEdition(Integer editionId, Integer projectId) {
        ScriptEdition edition = scriptEditionRepository.findByIdAndProjectId(editionId, projectId).orElse(null);
        if (edition == null) {
            return false;
        }
        if (edition.isDefault()) {
            return true;
        }
        for (ScriptEdition other : scriptEditionRepository.findByProjectIdOrderByNameAsc(projectId)) {
            boolean shouldBeDefault = other.getId().equals(editionId);
            if (other.isDefault() != shouldBeDefault) {
                other.setDefault(shouldBeDefault);
                other.setUpdatedAt(LocalDateTime.now());
                scriptEditionRepository.save(other);
            }
        }
        projectActivityService.recordForCurrentUser(
                projectId,
                ProjectActivity.ACTION_SCRIPT_EDITED,
                "set \"" + edition.getName() + "\" as default version",
                ProjectActivity.ENTITY_PROJECT,
                edition.getId());
        return true;
    }

    @Override
    @Transactional
    public boolean setPublishedEdition(Integer editionId, Integer projectId) {
        ScriptEdition edition = scriptEditionRepository.findByIdAndProjectId(editionId, projectId).orElse(null);
        if (edition == null) {
            return false;
        }
        if (edition.isPublished()) {
            return true;
        }
        for (ScriptEdition other : scriptEditionRepository.findByProjectIdOrderByNameAsc(projectId)) {
            boolean shouldBePublished = other.getId().equals(editionId);
            if (other.isPublished() != shouldBePublished) {
                other.setPublished(shouldBePublished);
                other.setUpdatedAt(LocalDateTime.now());
                scriptEditionRepository.save(other);
            }
        }
        projectActivityService.recordForCurrentUser(
                projectId,
                ProjectActivity.ACTION_SCRIPT_EDITED,
                "shared \"" + edition.getName() + "\" with the team",
                ProjectActivity.ENTITY_PROJECT,
                edition.getId());
        return true;
    }

    @Override
    @Transactional
    public void touchEdition(ScriptEdition edition) {
        if (edition == null || edition.getId() == null) {
            return;
        }
        // Reload by id. Callers often pass a lazy proxy that was detached by
        // clearAutomatically bulk order updates (create-below, move, delete).
        // Mutating that proxy throws LazyInitializationException and rolls back
        // the whole create — Enter-to-new-block looks like a silent no-op.
        ScriptEdition managed = scriptEditionRepository.findById(edition.getId()).orElse(null);
        if (managed == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        managed.setLastEdited(now);
        managed.setUpdatedAt(now);
        scriptEditionRepository.save(managed);
        Project project = managed.getProject();
        if (project != null) {
            project.setLastEdited(now);
            projectRepository.save(project);
        }
    }

    private String uniqueName(Integer projectId, String desired) {
        if (!scriptEditionRepository.existsByProjectIdAndNameIgnoreCase(projectId, desired)) {
            return desired;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = desired + " (" + i + ")";
            if (candidate.length() > 100) {
                candidate = desired.substring(0, Math.max(1, 100 - (" (" + i + ")").length())) + " (" + i + ")";
            }
            if (!scriptEditionRepository.existsByProjectIdAndNameIgnoreCase(projectId, candidate)) {
                return candidate;
            }
        }
        return desired + " " + System.currentTimeMillis();
    }

    private void copyScreenplay(ScriptEdition source, ScriptEdition target) {
        List<Person> sourcePersons = personRepository.findByScriptEditionIdOrderByNameAsc(source.getId());
        Map<Integer, Person> personMap = new HashMap<>();
        for (Person sourcePerson : sourcePersons) {
            Person copy = new Person();
            copy.setName(sourcePerson.getName());
            copy.setFullName(sourcePerson.getFullName());
            copy.setActor(sourcePerson.getActor());
            copy.setProject(target.getProject());
            copy.setScriptEdition(target);
            copy = personRepository.save(copy);
            personMap.put(sourcePerson.getId(), copy);
        }

        List<Block> sourceBlocks = blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(source.getId());
        for (Block sourceBlock : sourceBlocks) {
            Block copy = new Block();
            copy.setOrder(sourceBlock.getOrder());
            copy.setContent(sourceBlock.getContent());
            copy.setBookmarked(sourceBlock.isBookmarked());
            copy.setPinned(sourceBlock.isPinned());
            copy.setType(sourceBlock.getType());
            copy.setSceneDelimiter(sourceBlock.isSceneDelimiter());
            copy.setTextAlign(sourceBlock.getTextAlign());
            copy.setFont(sourceBlock.getFont());
            copy.setHighlight(sourceBlock.getHighlight());
            copy.setTextBold(sourceBlock.isTextBold());
            copy.setTextItalic(sourceBlock.isTextItalic());
            copy.setTextUnderline(sourceBlock.isTextUnderline());
            copy.setTags(sourceBlock.getTags());
            copy.setSourceDocumentId(sourceBlock.getSourceDocumentId());
            copy.setProject(target.getProject());
            copy.setScriptEdition(target);
            if (sourceBlock.getPerson() != null) {
                copy.setPerson(personMap.get(sourceBlock.getPerson().getId()));
            }
            blockRepository.save(copy);
        }
    }
}
