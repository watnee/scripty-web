package com.scripty.repository;

import com.scripty.dto.Invitation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, Integer> {

    Optional<Invitation> findByToken(String token);

    @Query("SELECT i FROM Invitation i JOIN FETCH i.team LEFT JOIN FETCH i.project WHERE i.token = :token")
    Optional<Invitation> findWithDetailsByToken(@Param("token") String token);

    @Query("SELECT i FROM Invitation i JOIN FETCH i.team WHERE i.project.id = :projectId AND i.status = :status ORDER BY i.createdAt DESC")
    List<Invitation> findByProjectIdAndStatus(@Param("projectId") Integer projectId, @Param("status") String status);

    @Query("SELECT i FROM Invitation i JOIN FETCH i.team WHERE i.team.id = :teamId AND i.status = :status ORDER BY i.createdAt DESC")
    List<Invitation> findByTeamIdAndStatus(@Param("teamId") Integer teamId, @Param("status") String status);

    Optional<Invitation> findFirstByEmailIgnoreCaseAndTeamIdAndStatus(String email, Integer teamId, String status);
}
