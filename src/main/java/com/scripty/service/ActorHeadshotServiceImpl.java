package com.scripty.service;

import com.scripty.dto.Actor;
import com.scripty.repository.ActorRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    private final Path headshotsDir;
    private final long maxBytes;

    @Autowired
    public ActorHeadshotServiceImpl(ActorRepository actorRepository,
                                    @Value("${app.uploads-dir:./uploads}") String uploadsDir,
                                    @Value("${app.headshot-max-bytes:5242880}") long maxBytes) {
        this.actorRepository = actorRepository;
        this.headshotsDir = Path.of(uploadsDir).resolve("headshots").toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    @Override
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
        deleteHeadshotFile(actor);

        String storedPath = storeHeadshot(actor.getId(), headshot);
        actor.setHeadshotPath(storedPath);
        actorRepository.save(actor);
    }

    @Override
    public void deleteHeadshot(Actor actor) {
        if (actor == null) {
            return;
        }
        deleteHeadshotFile(actor);
    }

    @Override
    public Optional<Resource> loadHeadshot(Integer actorId) {
        return actorRepository.findById(actorId)
                .flatMap(actor -> resolveHeadshotPath(actor).map(FileSystemResource::new));
    }

    @Override
    public Optional<String> getContentType(Integer actorId) {
        return actorRepository.findById(actorId)
                .flatMap(this::resolveHeadshotPath)
                .flatMap(this::probeContentType);
    }

    @Override
    public boolean hasHeadshot(Actor actor) {
        return actor != null
                && actor.getHeadshotPath() != null
                && !actor.getHeadshotPath().isBlank()
                && resolveHeadshotPath(actor).isPresent();
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
        try {
            Files.createDirectories(headshotsDir);
            String contentType = normalizeContentType(headshot.getContentType());
            String extension = EXTENSIONS.getOrDefault(contentType, ".jpg");
            String relativePath = "headshots/" + actorId + extension;
            Path destination = headshotsDir.resolve(actorId + extension);
            Files.copy(headshot.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return relativePath;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save headshot.", ex);
        }
    }

    private void deleteHeadshotFile(Actor actor) {
        resolveHeadshotPath(actor).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Best effort cleanup.
            }
        });

        if (actor.getId() != null) {
            for (String extension : EXTENSIONS.values()) {
                try {
                    Files.deleteIfExists(headshotsDir.resolve(actor.getId() + extension));
                } catch (IOException ignored) {
                    // Best effort cleanup.
                }
            }
        }
    }

    private Optional<Path> resolveHeadshotPath(Actor actor) {
        if (actor.getHeadshotPath() == null || actor.getHeadshotPath().isBlank()) {
            return Optional.empty();
        }

        Path path = headshotsDir.resolve(extractFilename(actor.getHeadshotPath())).normalize();
        if (!path.startsWith(headshotsDir) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private String extractFilename(String headshotPath) {
        int slash = headshotPath.lastIndexOf('/');
        return slash >= 0 ? headshotPath.substring(slash + 1) : headshotPath;
    }

    private Optional<String> probeContentType(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            return Optional.ofNullable(normalizeContentType(contentType));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        return contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
    }
}
