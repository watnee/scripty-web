package com.scripty.repository;

import com.scripty.dto.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Integer> {
    Optional<Team> findByName(String name);
    List<Team> findAllByOrderByNameAsc();
}
