package com.scripty.repository;

import com.scripty.dto.Actor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActorRepository extends JpaRepository<Actor, Integer> {

    List<Actor> findAllByOrderByFirstNameAsc();
}
