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
     * Drops the screenplay as anyone's default when it goes to the trash. The FK only clears
     * itself on a real delete, so without this a trashed screenplay stays someone's default
     * and the dashboard resolves to something they can no longer open.
     */
    @Modifying
    @Query(value = "UPDATE `user` SET default_project_id = NULL WHERE default_project_id = :projectId",
            nativeQuery = true)
    int clearDefaultProject(@Param("projectId") Integer projectId);
}
