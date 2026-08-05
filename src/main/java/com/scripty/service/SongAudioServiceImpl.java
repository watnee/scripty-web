package com.scripty.service;

import com.scripty.dto.SongAudio;
import com.scripty.dto.TextDocument;
import com.scripty.repository.SongAudioRepository;
import com.scripty.repository.TextDocumentRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Keeps a song's recordings in {@code song_audio} (what they are) and
 * {@code song_audio_data} (what they sound like), written in one transaction so
 * a row never exists without its bytes.
 *
 * <p>The split is the point: {@link #listForDocument} runs on every visit to a
 * song and touches no audio at all, while {@link #loadData} — the only method
 * that moves megabytes — runs when somebody presses play. Same arrangement
 * {@link ActorHeadshotServiceImpl} uses for headshots, for the same reason, and
 * in the database rather than on disk for the other reason: this application
 * deploys with no persistent volume.
 */
@Service
public class SongAudioServiceImpl implements SongAudioService {

    /**
     * What counts as audio. Deliberately a list and not a prefix test on
     * {@code audio/}: the browser and the phone both send types this
     * application then hands straight back to a player, and an unbounded set
     * means storing whatever a caller cares to label as sound.
     *
     * <p>The duplicates are real. {@code .m4a} arrives as {@code audio/mp4}
     * from one uploader, {@code audio/x-m4a} from another and
     * {@code audio/m4a} from a third, and {@code .wav} has three spellings of
     * its own — none of them wrong, all of them the same file.
     */
    private static final Map<String, String> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/mp3", ".mp3"),
            Map.entry("audio/mp4", ".m4a"),
            Map.entry("audio/m4a", ".m4a"),
            Map.entry("audio/x-m4a", ".m4a"),
            Map.entry("audio/aac", ".aac"),
            Map.entry("audio/wav", ".wav"),
            Map.entry("audio/wave", ".wav"),
            Map.entry("audio/x-wav", ".wav"),
            Map.entry("audio/vnd.wave", ".wav"),
            Map.entry("audio/aiff", ".aiff"),
            Map.entry("audio/x-aiff", ".aiff"),
            Map.entry("audio/flac", ".flac"),
            Map.entry("audio/x-flac", ".flac"),
            Map.entry("audio/ogg", ".ogg"),
            Map.entry("audio/opus", ".opus"),
            Map.entry("audio/webm", ".webm"),
            Map.entry("audio/3gpp", ".3gp"));

    /**
     * The same set read from the other end, for the uploader that sends no
     * type at all — a browser handed a file it does not recognise sends
     * {@code application/octet-stream}, and refusing a perfectly ordinary
     * {@code .m4a} over that would be a bad answer to a good file.
     */
    private static final Map<String, String> TYPES_BY_EXTENSION = Map.ofEntries(
            Map.entry(".mp3", "audio/mpeg"),
            Map.entry(".m4a", "audio/mp4"),
            Map.entry(".mp4", "audio/mp4"),
            Map.entry(".aac", "audio/aac"),
            Map.entry(".wav", "audio/wav"),
            Map.entry(".aif", "audio/aiff"),
            Map.entry(".aiff", "audio/aiff"),
            Map.entry(".flac", "audio/flac"),
            Map.entry(".ogg", "audio/ogg"),
            Map.entry(".oga", "audio/ogg"),
            Map.entry(".opus", "audio/opus"),
            Map.entry(".webm", "audio/webm"),
            Map.entry(".3gp", "audio/3gpp"),
            Map.entry(".caf", "audio/x-caf"));

    /** As many takes as a song can reasonably carry before the list stops being one. */
    private static final int MAX_PER_SONG = 50;

    private final SongAudioRepository songAudioRepository;
    private final TextDocumentRepository textDocumentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final long maxBytes;

    @Autowired
    public SongAudioServiceImpl(SongAudioRepository songAudioRepository,
                                TextDocumentRepository textDocumentRepository,
                                JdbcTemplate jdbcTemplate,
                                @Value("${app.song-audio-max-bytes:26214400}") long maxBytes) {
        this.songAudioRepository = songAudioRepository;
        this.textDocumentRepository = textDocumentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.maxBytes = maxBytes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SongAudio> listForDocument(Integer documentId) {
        if (documentId == null) {
            return List.of();
        }
        return songAudioRepository.findByTextDocumentIdOrderBySortOrderAscIdAsc(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SongAudio> find(Integer documentId, Integer audioId) {
        if (documentId == null || audioId == null) {
            return Optional.empty();
        }
        return songAudioRepository.findById(audioId)
                .filter(audio -> audio.getTextDocument() != null
                        && documentId.equals(audio.getTextDocument().getId()));
    }

    @Override
    @Transactional
    public SongAudio store(Integer documentId, MultipartFile file, String title, Integer durationMs) {
        TextDocument document = requireSong(documentId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose an audio file to add.");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "That recording is too large. The limit is " + megabytes(maxBytes) + " MB.");
        }
        if (songAudioRepository.countByTextDocumentId(documentId) >= MAX_PER_SONG) {
            throw new IllegalArgumentException(
                    "This song already has " + MAX_PER_SONG + " recordings. Delete one to add another.");
        }

        String fileName = cleanFileName(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType(), fileName);
        if (contentType == null) {
            throw new IllegalArgumentException(
                    "That file is not audio. Add an MP3, M4A, WAV, AIFF, FLAC or OGG recording.");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save that recording.", ex);
        }

        SongAudio audio = new SongAudio();
        audio.setTextDocument(document);
        audio.setTitle(cleanTitle(title, fileName));
        audio.setFileName(fileName);
        audio.setContentType(contentType);
        audio.setByteSize(data.length);
        audio.setDurationMs(cleanDuration(durationMs));
        audio.setSortOrder(songAudioRepository.nextSortOrder(documentId));
        audio.setCreatedAt(LocalDateTime.now());
        SongAudio saved = songAudioRepository.saveAndFlush(audio);

        // The blob goes in by hand, one statement, keyed by the id the flush
        // above just minted. Both halves are in this transaction, so a failure
        // here takes the metadata row with it rather than leaving a recording
        // in the list with nothing behind it.
        jdbcTemplate.update("INSERT INTO song_audio_data (song_audio_id, data) VALUES (?, ?)",
                saved.getId(), data);
        return saved;
    }

    @Override
    @Transactional
    public Optional<SongAudio> rename(Integer documentId, Integer audioId, String title) {
        Optional<SongAudio> found = find(documentId, audioId);
        found.ifPresent(audio -> {
            audio.setTitle(cleanTitle(title, audio.getFileName()));
            songAudioRepository.save(audio);
        });
        return found;
    }

    @Override
    @Transactional
    public boolean delete(Integer documentId, Integer audioId) {
        Optional<SongAudio> found = find(documentId, audioId);
        if (found.isEmpty()) {
            return false;
        }
        // The bytes go first and by hand. ON DELETE CASCADE would take them
        // too, but only on a database that enforces it, and deleting the large
        // half explicitly says what is happening here.
        jdbcTemplate.update("DELETE FROM song_audio_data WHERE song_audio_id = ?", audioId);
        songAudioRepository.delete(found.get());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Resource> loadData(Integer documentId, Integer audioId) {
        if (find(documentId, audioId).isEmpty()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                        "SELECT data FROM song_audio_data WHERE song_audio_id = ?",
                        (rs, rowNum) -> rs.getBytes(1),
                        audioId)
                .stream()
                .findFirst()
                .map(ByteArrayResource::new);
    }

    @Override
    public long maxBytes() {
        return maxBytes;
    }

    /**
     * The song this is about, or a refusal naming what is wrong. Archived songs
     * are included on purpose: one is still opened and edited by id, and a
     * recording is part of what a writer came back for.
     */
    private TextDocument requireSong(Integer documentId) {
        TextDocument document = documentId == null ? null
                : textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (document == null) {
            throw new IllegalArgumentException("That song no longer exists.");
        }
        if (!TextDocument.TYPE_SONG.equalsIgnoreCase(document.getDocumentType())) {
            throw new IllegalArgumentException("Only songs can hold recordings.");
        }
        return document;
    }

    /**
     * The type to serve this file back as, or null if it is not audio.
     *
     * <p>What the uploader said comes first, and the extension is the fallback
     * — for the browser that sends {@code application/octet-stream} because it
     * has never seen an {@code .aiff}, and for the client that sends nothing.
     * A file that is audio by neither reading is refused.
     */
    private String resolveContentType(String declared, String fileName) {
        String normalized = normalize(declared);
        if (normalized != null && ALLOWED_TYPES.containsKey(normalized)) {
            return normalized;
        }
        String extension = extensionOf(fileName);
        return extension == null ? null : TYPES_BY_EXTENSION.get(extension);
    }

    private String normalize(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        return contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    /**
     * A file name safe to hand back in a {@code Content-Disposition} and short
     * enough for the column. Path separators go: a name is a name, never a
     * route, and some browsers still send the whole path.
     */
    private String cleanFileName(String original) {
        String name = original == null ? "" : original.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\"]", "");
        if (name.isBlank()) {
            name = "recording";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    /** The given name, or the file's own with its extension taken off. */
    private String cleanTitle(String title, String fileName) {
        String cleaned = title == null ? "" : title.trim();
        if (cleaned.isBlank()) {
            int dot = fileName.lastIndexOf('.');
            cleaned = dot > 0 ? fileName.substring(0, dot) : fileName;
        }
        if (cleaned.isBlank()) {
            cleaned = "Recording";
        }
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    /**
     * A duration worth keeping. Nobody's clock is trusted here — this arrives
     * from a browser or a phone as a number in a form field — so a negative or
     * absurd one becomes "unknown", which every screen already draws.
     */
    private Integer cleanDuration(Integer durationMs) {
        if (durationMs == null || durationMs <= 0) {
            return null;
        }
        // Twelve hours. Past that it is a mistyped field, not a demo.
        return durationMs > 43_200_000 ? null : durationMs;
    }

    private long megabytes(long bytes) {
        return Math.max(1, bytes / (1024 * 1024));
    }
}
