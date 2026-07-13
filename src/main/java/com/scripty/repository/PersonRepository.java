package com.scripty.repository;

import com.scripty.dto.Person;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, Integer> {

    List<Person> findByProjectIdOrderByNameAsc(Integer projectId);

    List<Person> findByScriptEditionIdOrderByNameAsc(Integer scriptEditionId);

    List<Person> findByActorIdOrderByNameAsc(Integer actorId);

    @Query("SELECT p FROM Person p JOIN FETCH p.project WHERE p.id = :id AND p.project.id = :projectId")
    Optional<Person> findByIdAndProjectId(@Param("id") Integer id, @Param("projectId") Integer projectId);

    @Query("SELECT p FROM Person p JOIN FETCH p.project WHERE p.id = :id AND p.scriptEdition.id = :scriptEditionId")
    Optional<Person> findByIdAndScriptEditionId(@Param("id") Integer id, @Param("scriptEditionId") Integer scriptEditionId);
}
