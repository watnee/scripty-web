package com.scripty.repository;

import com.scripty.dto.SongAudio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongAudioRepository extends JpaRepository<SongAudio, Integer> {

    /**
     * A song's recordings in the order they are shown: the writer's own order
     * first, and oldest-first within it, so a newly uploaded take lands at the
     * bottom rather than somewhere in the middle.
     */
    List<SongAudio> findByTextDocumentIdOrderBySortOrderAscIdAsc(Integer textDocumentId);

    /** The next free place at the end of a song's list. */
    @Query("SELECT COALESCE(MAX(a.sortOrder), -1) + 1 FROM SongAudio a "
            + "WHERE a.textDocument.id = :textDocumentId")
    int nextSortOrder(@Param("textDocumentId") Integer textDocumentId);

    long countByTextDocumentId(Integer textDocumentId);
}
