package com.scripty.service;

import com.scripty.dto.Actor;
import com.scripty.dto.Audition;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.repository.ActorRepository;
import com.scripty.repository.AuditionRepository;
import com.scripty.repository.PersonRepository;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditionServiceImpl implements AuditionService {

    private final AuditionRepository auditionRepository;
    private final ActorRepository actorRepository;
    private final PersonRepository personRepository;

    @Autowired
    public AuditionServiceImpl(AuditionRepository auditionRepository,
                               ActorRepository actorRepository,
                               PersonRepository personRepository) {
        this.auditionRepository = auditionRepository;
        this.actorRepository = actorRepository;
        this.personRepository = personRepository;
    }

    @Override
    @Transactional
    public void setAuditionsForActorInProject(Integer actorId, Integer projectId, List<Integer> characterIds) {
        Actor actor = actorRepository.findById(actorId).orElse(null);
        if (actor == null || projectId == null || !actorBelongsToProject(actor, projectId)) {
            return;
        }

        List<Audition> existing = auditionRepository.findByActorIdAndProjectId(actorId, projectId);
        auditionRepository.deleteAll(existing);

        if (characterIds == null || characterIds.isEmpty()) {
            return;
        }

        for (Integer characterId : new HashSet<>(characterIds)) {
            Person person = personRepository.findById(characterId).orElse(null);
            if (person == null || person.getProject() == null || !projectId.equals(person.getProject().getId())) {
                continue;
            }
            Audition audition = new Audition();
            audition.setActor(actor);
            audition.setPerson(person);
            auditionRepository.save(audition);
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
}
