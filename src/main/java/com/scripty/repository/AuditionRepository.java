package com.scripty.repository;

import com.scripty.dto.Audition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditionRepository extends JpaRepository<Audition, Integer> {

    @Query("SELECT a FROM Audition a JOIN FETCH a.actor JOIN FETCH a.person p WHERE p.project.id = :projectId")
    List<Audition> findByProjectId(@Param("projectId") Integer projectId);

    @Query("SELECT a FROM Audition a JOIN FETCH a.person p WHERE a.actor.id = :actorId AND p.project.id = :projectId")
    List<Audition> findByActorIdAndProjectId(@Param("actorId") Integer actorId, @Param("projectId") Integer projectId);

    @Query("SELECT a FROM Audition a JOIN FETCH a.person p JOIN FETCH p.project WHERE a.actor.id = :actorId")
    List<Audition> findByActorId(@Param("actorId") Integer actorId);
}
