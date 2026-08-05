package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.dto.SongAudio;
import com.scripty.dto.TextDocument;
import com.scripty.repository.SongAudioRepository;
import com.scripty.repository.TextDocumentRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.web.MockMultipartFile;

/**
 * The bytes go through real H2 running the real migration, the way
 * {@link ActorHeadshotServiceImplTest} does — so the SQL and the foreign key
 * between a recording and its data are exercised rather than described. The
 * JPA half is mocked, with {@code saveAndFlush} standing in for Hibernate by
 * writing the metadata row itself and handing back the id it minted; without
 * that row the blob insert would have nothing to point at, which is exactly the
 * constraint under test.
 */
class SongAudioServiceImplTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private SongAudioRepository songAudioRepository;
    private TextDocumentRepository textDocumentRepository;
    private SongAudioServiceImpl service;
    private final List<SongAudio> stored = new ArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    @BeforeEach
    void setUp() throws IOException {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:songaudio;MODE=MySQL;DATABASE_TO_UPPER=false", true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE text_document (id int PRIMARY KEY)");
        jdbcTemplate.update("INSERT INTO text_document (id) VALUES (7)");
        runMigration("db/migration/V60__create_song_audio.sql");

        songAudioRepository = mock(SongAudioRepository.class);
        textDocumentRepository = mock(TextDocumentRepository.class);
        service = new SongAudioServiceImpl(songAudioRepository, textDocumentRepository,
                jdbcTemplate, 1_000_000);

        when(textDocumentRepository.findByIdAndDeletedAtIsNull(7)).thenReturn(Optional.of(song(7)));
        when(songAudioRepository.nextSortOrder(any())).thenAnswer(call -> stored.size());
        when(songAudioRepository.countByTextDocumentId(any())).thenAnswer(call -> (long) stored.size());
        when(songAudioRepository.saveAndFlush(any(SongAudio.class))).thenAnswer(call -> {
            SongAudio audio = call.getArgument(0);
            audio.setId(nextId.getAndIncrement());
            jdbcTemplate.update("INSERT INTO song_audio (id, text_document_id, title, file_name,"
                            + " content_type, byte_size, duration_ms, sort_order) VALUES (?,?,?,?,?,?,?,?)",
                    audio.getId(), audio.getTextDocument().getId(), audio.getTitle(),
                    audio.getFileName(), audio.getContentType(), audio.getByteSize(),
                    audio.getDurationMs(), audio.getSortOrder());
            stored.add(audio);
            return audio;
        });
        when(songAudioRepository.findById(any())).thenAnswer(call -> stored.stream()
                .filter(audio -> call.getArgument(0).equals(audio.getId()))
                .findFirst());
    }

    private void runMigration(String path) throws IOException {
        String migration = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        for (String statement : migration.split(";")) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    private TextDocument song(Integer id) {
        TextDocument document = new TextDocument();
        document.setId(id);
        document.setDocumentType(TextDocument.TYPE_SONG);
        return document;
    }

    private MockMultipartFile file(String name, String contentType, int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) i;
        }
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    @Test
    void storesTheBytesAndTheDescriptionOfThem() throws IOException {
        SongAudio saved = service.store(7, file("chorus.m4a", "audio/mp4", 64), null, 91_000);

        assertEquals("chorus", saved.getTitle(), "names itself after the file, extension off");
        assertEquals("chorus.m4a", saved.getFileName());
        assertEquals("audio/mp4", saved.getContentType());
        assertEquals(64, saved.getByteSize());
        assertEquals(91_000, saved.getDurationMs());
        assertNotNull(saved.getCreatedAt());

        Resource data = service.loadData(7, saved.getId()).orElseThrow();
        assertEquals(64, data.contentLength(), "the bytes come back as they went in");
    }

    @Test
    void keepsTheNameTheWriterGaveIt() {
        SongAudio saved = service.store(7, file("voice-memo-4.m4a", "audio/mp4", 8),
                "  Chorus idea, 2am  ", null);
        assertEquals("Chorus idea, 2am", saved.getTitle());
        assertEquals("voice-memo-4.m4a", saved.getFileName(), "renaming does not rename the file");
    }

    @Test
    void readsTheExtensionWhenTheUploaderSaysNothingUseful() {
        SongAudio saved = service.store(7, file("demo.wav", "application/octet-stream", 8), null, null);
        assertEquals("audio/wav", saved.getContentType());
    }

    @Test
    void refusesWhatIsNotAudio() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> service.store(7, file("lyrics.pdf", "application/pdf", 8), null, null));
        assertTrue(refused.getMessage().contains("not audio"), refused.getMessage());
    }

    @Test
    void refusesWhatIsTooLarge() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> service.store(7, file("bounce.wav", "audio/wav", 1_000_001), null, null));
        assertTrue(refused.getMessage().contains("too large"), refused.getMessage());
    }

    @Test
    void refusesANoteAndAVanishedDocument() {
        TextDocument note = song(9);
        note.setDocumentType(TextDocument.TYPE_NOTES);
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(9)).thenReturn(Optional.of(note));
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(404)).thenReturn(Optional.empty());

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.store(9, file("demo.mp3", "audio/mpeg", 8), null, null))
                .getMessage().contains("Only songs"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.store(404, file("demo.mp3", "audio/mpeg", 8), null, null))
                .getMessage().contains("no longer exists"));
    }

    @Test
    void forgetsADurationNobodyCouldHaveMeasured() {
        assertNull(service.store(7, file("a.mp3", "audio/mpeg", 8), null, -1).getDurationMs());
        assertNull(service.store(7, file("b.mp3", "audio/mpeg", 8), null, 999_999_999).getDurationMs());
    }

    @Test
    void aRecordingIsOnlyReachableThroughItsOwnSong() {
        SongAudio saved = service.store(7, file("demo.mp3", "audio/mpeg", 8), null, null);

        assertTrue(service.find(7, saved.getId()).isPresent());
        assertTrue(service.find(8, saved.getId()).isEmpty(), "another song's id finds nothing");
        assertTrue(service.loadData(8, saved.getId()).isEmpty());
        assertFalse(service.delete(8, saved.getId()));
        assertTrue(service.rename(8, saved.getId(), "Mine now").isEmpty());
    }

    @Test
    void deletingTakesTheBytesWithIt() {
        SongAudio saved = service.store(7, file("demo.mp3", "audio/mpeg", 8), null, null);
        assertTrue(service.loadData(7, saved.getId()).isPresent());

        assertTrue(service.delete(7, saved.getId()));
        stored.clear();

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM song_audio_data WHERE song_audio_id = ?",
                Integer.class, saved.getId());
        assertEquals(0, rows);
    }

    @Test
    void deletingTheSongTakesTheRecordingsWithIt() {
        SongAudio saved = service.store(7, file("demo.mp3", "audio/mpeg", 8), null, null);

        // The cascade the migration declares, exercised rather than assumed:
        // a song thrown away for good leaves no audio behind in the database.
        jdbcTemplate.update("DELETE FROM text_document WHERE id = ?", 7);

        assertEquals(0, (int) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM song_audio WHERE id = ?", Integer.class, saved.getId()));
        assertEquals(0, (int) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM song_audio_data WHERE song_audio_id = ?",
                Integer.class, saved.getId()));
    }
}
