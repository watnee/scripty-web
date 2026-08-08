package com.scripty.repository;

import com.scripty.dto.SongVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * The ids of a song version's auto-saves, newest first — the screenplay's
     * {@link ProjectVersionRepository#findAutoSaveIdsByScriptEditionIdOrderByCreatedAtDesc}
     * for songs, and ids-only for the same reason: this runs after every
     * auto-save, and each row carries the whole lyric as {@code snapshot_json}.
     */
    @Query("""
            SELECT v.id FROM SongVersion v
            WHERE v.songEdition.id = :songEditionId
              AND v.label LIKE 'Auto-save%'
            ORDER BY v.createdAt DESC, v.id DESC
            """)
    List<Integer> findAutoSaveIdsBySongEditionIdOrderByCreatedAtDesc(
            @Param("songEditionId") Integer songEditionId);

    /** Deletes the named versions in one statement; see the screenplay's counterpart. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SongVersion v WHERE v.id IN :ids")
    int deleteAllByIdIn(@Param("ids") List<Integer> ids);
}
