package com.scripty.repository;

import com.scripty.dto.Authority;
import com.scripty.dto.AuthorityId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorityRepository extends JpaRepository<Authority, AuthorityId> {

    List<Authority> findByUsername(String username);

    void deleteByUsername(String username);
}
