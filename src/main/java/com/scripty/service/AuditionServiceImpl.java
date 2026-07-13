package com.scripty.service;

import com.scripty.dto.Actor;
import com.scripty.dto.Audition;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.repository.ActorRepository;
import com.scripty.repository.AuditionRepository;
import com.scripty.repository.PersonRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditionServiceImpl implements AuditionService {

    private final AuditionRepository auditionRepository;
    private final ActorRepository actorRepository;
    private final PersonRepository personRepository;
    private final ProjectActivityService projectActivityService;

    @Autowired
    public AuditionServiceImpl(AuditionRepository auditionRepository,
                               ActorRepository actorRepository,
                               PersonRepository personRepository,
                               ProjectActivityService projectActivityService) {
        this.auditionRepository = auditionRepository;
        this.actorRepository = actorRepository;
        this.personRepository = personRepository;
        this.projectActivityService = projectActivityService;
    }

    @Override
    @Transactional
    public void setAuditionsForActorInProject(Integer actorId, Integer projectId, List<Integer> characterIds) {
        Actor actor = actorRepository.findByIdWithProjects(actorId).orElse(null);
        if (actor == null || projectId == null || !actorBelongsToProject(actor, projectId)) {
            return;
        }

        Set<Integer> desiredCharacterIds = characterIds == null
                ? Set.of()
                : new HashSet<>(characterIds);

        List<Audition> existing = auditionRepository.findByActorIdAndProjectId(actorId, projectId);
        boolean changed = false;
        for (Audition audition : existing) {
            Integer personId = audition.getPerson().getId();
            if (desiredCharacterIds.contains(personId)) {
                desiredCharacterIds.remove(personId);
            } else {
                auditionRepository.delete(audition);
                changed = true;
            }
        }

        for (Integer characterId : desiredCharacterIds) {
            Person person = personRepository.findByIdAndProjectId(characterId, projectId).orElse(null);
            if (person == null) {
                continue;
            }
            Audition audition = new Audition();
            audition.setActor(actor);
            audition.setPerson(person);
            auditionRepository.save(audition);
            changed = true;
        }

        if (changed) {
            projectActivityService.recordForCurrentUser(
                    projectId,
                    ProjectActivity.ACTION_AUDITIONS_UPDATED,
                    "updated auditions for " + formatActorName(actor),
                    ProjectActivity.ENTITY_AUDITION,
                    actorId);
        }
    }

    private boolean actorBelongsToProject(Actor actor, Integer projectId) {
        for (Project project : actor.getProjects()) {
            if (projectId.equals(project.getId())) {
                return true;
            }
        }
        return false;
    }

    private static String formatActorName(Actor actor) {
        if (actor.getLastName() == null || actor.getLastName().isBlank()) {
            return actor.getFirstName();
        }
        return actor.getFirstName() + " " + actor.getLastName();
    }
}
