package com.scripty.repository;

import com.scripty.dto.SongEdition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongEditionRepository extends JpaRepository<SongEdition, Integer> {

    List<SongEdition> findByTextDocumentIdOrderByNameAsc(Integer textDocumentId);

    Optional<SongEdition> findByIdAndTextDocumentId(Integer id, Integer textDocumentId);

    @Query("SELECT e FROM SongEdition e WHERE e.textDocument.id = :documentId AND e.isDefault = TRUE")
    Optional<SongEdition> findDefaultByTextDocumentId(@Param("documentId") Integer documentId);

    @Query("SELECT e FROM SongEdition e WHERE e.textDocument.id = :documentId AND e.isPublished = TRUE")
    Optional<SongEdition> findPublishedByTextDocumentId(@Param("documentId") Integer documentId);

    boolean existsByTextDocumentIdAndNameIgnoreCase(Integer textDocumentId, String name);

    long countByTextDocumentId(Integer textDocumentId);
}
