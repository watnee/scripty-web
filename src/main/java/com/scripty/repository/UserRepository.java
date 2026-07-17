package com.scripty.repository;

import com.scripty.dto.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findAllByOrderByUsernameAsc();

    /**
     * Unsets the project as anyone's default. Called when a project is trashed:
     * the column's ON DELETE SET NULL only fires on the eventual purge, which
     * would leave users defaulting to a project they cannot open until then.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.defaultProjectId = NULL WHERE u.defaultProjectId = :projectId")
    int clearDefaultProject(@Param("projectId") Integer projectId);
}
