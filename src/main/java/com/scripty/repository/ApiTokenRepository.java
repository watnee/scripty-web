package com.scripty.repository;

import com.scripty.dto.ApiToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Integer> {

    Optional<ApiToken> findByTokenHash(String tokenHash);

    List<ApiToken> findByUsernameOrderByCreatedAtDesc(String username);
}
