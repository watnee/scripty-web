package com.scripty.repository;

import com.scripty.dto.Person;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Integer> {

    List<Person> findByProjectIdOrderByNameAsc(Integer projectId);
}
