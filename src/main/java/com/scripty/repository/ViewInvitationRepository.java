package com.scripty.repository;

import com.scripty.dto.ViewInvitation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViewInvitationRepository extends JpaRepository<ViewInvitation, Integer> {

    @Query("SELECT v FROM ViewInvitation v JOIN FETCH v.project WHERE v.token = :token")
    Optional<ViewInvitation> findWithProjectByToken(@Param("token") String token);

    @Query("SELECT v FROM ViewInvitation v WHERE v.project.id = :projectId AND v.status = :status ORDER BY v.createdAt DESC")
    List<ViewInvitation> findByProjectIdAndStatus(@Param("projectId") Integer projectId, @Param("status") String status);

    Optional<ViewInvitation> findFirstByEmailIgnoreCaseAndProjectIdAndStatus(String email, Integer projectId, String status);
}
