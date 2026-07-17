package com.scripty.repository;

import com.scripty.dto.SongVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongVersionRepository extends JpaRepository<SongVersion, Integer> {

    List<SongVersion> findByTextDocumentIdOrderByCreatedAtDesc(Integer textDocumentId);

    SongVersion findFirstByTextDocumentIdOrderByCreatedAtDesc(Integer textDocumentId);

    @Query("""
            SELECT v FROM SongVersion v
            WHERE v.textDocument.id = :textDocumentId
              AND v.label LIKE 'Auto-save%'
            ORDER BY v.createdAt DESC, v.id DESC
            """)
    List<SongVersion> findAutoSavesByTextDocumentIdOrderByCreatedAtDesc(
            @Param("textDocumentId") Integer textDocumentId);

    List<SongVersion> findBySongEditionIdOrderByCreatedAtDesc(Integer songEditionId);

    SongVersion findFirstBySongEditionIdOrderByCreatedAtDesc(Integer songEditionId);

    @Query("""
            SELECT v FROM SongVersion v
            WHERE v.songEdition.id = :songEditionId
              AND v.label LIKE 'Auto-save%'
            ORDER BY v.createdAt DESC, v.id DESC
            """)
    List<SongVersion> findAutoSavesBySongEditionIdOrderByCreatedAtDesc(
            @Param("songEditionId") Integer songEditionId);
}
