package com.scripty.repository;

import com.scripty.dto.PasswordRecoveryToken;
import com.scripty.dto.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordRecoveryTokenRepository extends JpaRepository<PasswordRecoveryToken, Integer> {

    Optional<PasswordRecoveryToken> findByToken(String token);

    void deleteByUser(User user);
}
