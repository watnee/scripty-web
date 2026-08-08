package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.dto.Project;
import com.scripty.dto.TextDocument;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.SongBlockRepository;
import com.scripty.repository.TextDocumentRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The nightly document purge against a real database and the real schema.
 *
 * <p>{@link TextDocumentServiceImplTrashTest} covers what the service decides —
 * which cutoff, and that an unlimited retention sweeps nothing. This covers the
 * parts only a database has: that the purge takes the backlog a page at a time
 * and keeps going until it is gone, that one statement per page removes what a
 * delete per row used to, and that a song's lines go with it.
 *
 * <p>That last one is the risk in deleting by id rather than by entity. Nothing
 * cascades in the mapping — the lines are carried away by {@code ON DELETE
 * CASCADE} on {@code song_block}, which only a database can be asked about.
 */
@SpringBootTest
@ActiveProfiles("test")
class TextDocumentTrashPurgeIntegrationTest {

    /** Small enough that the documents below take several passes to clear. */
    private static final int BATCH_SIZE = 2;
    private static final int RETENTION_DAYS = 30;

    @Autowired
    private TextDocumentService textDocumentService;

    @Autowired
    private SongEditionService songEditionService;

    @Autowired
    private SongBlockService songBlockService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TextDocumentRepository textDocumentRepository;

    @Autowired
    private SongBlockRepository songBlockRepository;

    private Project project;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(textDocumentService, "trashRetentionDays", RETENTION_DAYS);
        ReflectionTestUtils.setField(textDocumentService, "purgeBatchSize", BATCH_SIZE);

        Project fresh = new Project();
        fresh.setTitle("Purge Songs");
        project = projectRepository.save(fresh);
    }

    /**
     * A song with a line of lyric, trashed {@code daysAgo} days ago, or live
     * when null.
     *
     * <p>Written and then trashed, in that order: the services that seed a
     * song's first line refuse a document that is already in the trash, which
     * is right of them and is also how a real song gets there.
     */
    private TextDocument givenASong(String title, Integer daysAgo) {
        TextDocument document = new TextDocument();
        document.setTitle(title);
        document.setDocumentType(TextDocument.TYPE_SONG);
        document.setProject(project);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        TextDocument saved = textDocumentRepository.save(document);

        // Reaching for the blocks seeds the one empty line a song always has,
        // which is what gives the cascade something to carry away.
        Integer editionId = songEditionService.ensureDefaultEdition(saved.getId()).getId();
        songBlockService.getBlocks(saved.getId(), editionId);

        if (daysAgo != null) {
            saved.setDeletedAt(LocalDateTime.now().minusDays(daysAgo));
            textDocumentRepository.save(saved);
        }
        return saved;
    }

    @Test
    void theBacklogIsClearedAPageAtATimeAndTakesItsLinesWithIt() {
        List<Integer> expired = new ArrayList<>();
        // Five is more than two pages' worth, so the sweep only finishes if it
        // goes back for another page after the first.
        for (int i = 1; i <= 5; i++) {
            expired.add(givenASong("Trashed " + i, RETENTION_DAYS + i).getId());
        }
        Integer stillInTheTrash = givenASong("Trashed yesterday", 1).getId();
        Integer live = givenASong("Still being written", null).getId();

        for (Integer id : expired) {
            assertTrue(songBlockRepository.countByTextDocumentId(id) > 0,
                    "the song should have a line for the purge to take with it");
        }

        assertEquals(expired.size(), textDocumentService.purgeExpired(),
                "every expired document, across as many pages as it takes");

        for (Integer id : expired) {
            assertTrue(textDocumentRepository.findById(id).isEmpty(), "purged document " + id);
            assertEquals(0, songBlockRepository.countByTextDocumentId(id),
                    "the purged document's lines go with it, by cascade");
        }
        assertFalse(textDocumentRepository.findById(stillInTheTrash).isEmpty(),
                "a document trashed yesterday is still recoverable");
        assertFalse(textDocumentRepository.findById(live).isEmpty(),
                "and a document nobody deleted is untouched");
    }

    @Test
    void anEmptyTrashIsSweptWithoutDeletingAnything() {
        Integer live = givenASong("Still being written", null).getId();

        assertEquals(0, textDocumentService.purgeExpired());
        assertFalse(textDocumentRepository.findById(live).isEmpty());
    }
}
