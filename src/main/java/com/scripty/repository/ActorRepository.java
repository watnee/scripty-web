package com.scripty.repository;

import com.scripty.dto.Actor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActorRepository extends JpaRepository<Actor, Integer> {

    List<Actor> findAllByOrderByFirstNameAsc();

    List<Actor> findDistinctByProjects_IdOrderByFirstNameAsc(Integer projectId);

    @Query("SELECT a FROM Actor a LEFT JOIN FETCH a.projects WHERE a.id = :id")
    Optional<Actor> findByIdWithProjects(@Param("id") Integer id);
}
