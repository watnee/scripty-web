package com.scripty.service;

import com.scripty.dto.Actor;
import com.scripty.repository.ActorRepository;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores headshots in the {@code actor_headshot} table (mediumblob) instead of
 * on disk, so the app needs no persistent volume and deploys can overlap.
 * {@code Actor.headshotPath} stays the "has a headshot" marker the templates
 * and API assembler already key off; it is set if and only if a blob row
 * exists (both are written in the same transaction).
 */
@Service
public class ActorHeadshotServiceImpl implements ActorHeadshotService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif");

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final ActorRepository actorRepository;
    private final JdbcTemplate jdbcTemplate;
    private final long maxBytes;

    @Autowired
    public ActorHeadshotServiceImpl(ActorRepository actorRepository,
                                    JdbcTemplate jdbcTemplate,
                                    @Value("${app.headshot-max-bytes:5242880}") long maxBytes) {
        this.actorRepository = actorRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.maxBytes = maxBytes;
    }

    @Override
    @Transactional
    public void updateHeadshot(Actor actor, MultipartFile headshot, boolean removeHeadshot) {
        if (actor == null) {
            return;
        }

        if (removeHeadshot) {
            deleteHeadshot(actor);
            actor.setHeadshotPath(null);
            actorRepository.save(actor);
            return;
        }

        if (headshot == null || headshot.isEmpty()) {
            return;
        }

        validateHeadshot(headshot);

        String storedPath = storeHeadshot(actor.getId(), headshot);
        actor.setHeadshotPath(storedPath);
        actorRepository.save(actor);
    }

    @Override
    @Transactional
    public void deleteHeadshot(Actor actor) {
        if (actor == null || actor.getId() == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM actor_headshot WHERE actor_id = ?", actor.getId());
    }

    @Override
    public Optional<Resource> loadHeadshot(Integer actorId) {
        if (actorId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                        "SELECT data FROM actor_headshot WHERE actor_id = ?",
                        (rs, rowNum) -> rs.getBytes(1),
                        actorId)
                .stream()
                .findFirst()
                .map(ByteArrayResource::new);
    }

    @Override
    public Optional<String> getContentType(Integer actorId) {
        if (actorId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                        "SELECT content_type FROM actor_headshot WHERE actor_id = ?",
                        (rs, rowNum) -> rs.getString(1),
                        actorId)
                .stream()
                .findFirst()
                .map(this::normalizeContentType);
    }

    @Override
    public boolean hasHeadshot(Actor actor) {
        return actor != null
                && actor.getHeadshotPath() != null
                && !actor.getHeadshotPath().isBlank();
    }

    private void validateHeadshot(MultipartFile headshot) {
        if (headshot.getSize() > maxBytes) {
            throw new IllegalArgumentException("Headshot must be 5 MB or smaller.");
        }

        String contentType = normalizeContentType(headshot.getContentType());
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Headshot must be a JPG, PNG, WebP, or GIF image.");
        }
    }

    private String storeHeadshot(Integer actorId, MultipartFile headshot) {
        byte[] data;
        try {
            data = headshot.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save headshot.", ex);
        }

        String contentType = normalizeContentType(headshot.getContentType());
        // Delete + insert instead of vendor upsert syntax: runs on both MySQL
        // (prod) and H2 (dev/tests), and replacements are rare.
        jdbcTemplate.update("DELETE FROM actor_headshot WHERE actor_id = ?", actorId);
        jdbcTemplate.update(
                "INSERT INTO actor_headshot (actor_id, content_type, data) VALUES (?, ?, ?)",
                actorId, contentType, data);
        return "headshots/" + actorId + EXTENSIONS.getOrDefault(contentType, ".jpg");
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        return contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
    }
}
