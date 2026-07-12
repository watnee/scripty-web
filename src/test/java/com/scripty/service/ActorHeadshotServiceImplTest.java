package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.scripty.dto.Actor;
import com.scripty.repository.ActorRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Runs against in-memory H2 in MySQL mode (what dev uses) with the real
 * migration DDL, so the SQL is exercised, not mocked.
 */
class ActorHeadshotServiceImplTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private ActorRepository actorRepository;
    private ActorHeadshotServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:headshots;MODE=MySQL;DATABASE_TO_UPPER=false", true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE actor (id int PRIMARY KEY, headshot_path varchar(255) NULL)");
        String migration = new ClassPathResource("db/migration/V35__actor_headshot_blob.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        for (String statement : migration.split(";")) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
        jdbcTemplate.update("INSERT INTO actor (id) VALUES (7)");

        actorRepository = mock(ActorRepository.class);
        service = new ActorHeadshotServiceImpl(actorRepository, jdbcTemplate, 5_242_880);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    private Actor actor(Integer id) {
        Actor actor = new Actor();
        actor.setId(id);
        return actor;
    }

    private MockMultipartFile png(byte[] bytes) {
        return new MockMultipartFile("headshot", "photo.png", "image/png", bytes);
    }

    @Test
    void storesAndLoadsHeadshot() throws IOException {
        Actor actor = actor(7);
        byte[] bytes = new byte[] {1, 2, 3, 4};

        service.updateHeadshot(actor, png(bytes), false);

        assertEquals("headshots/7.png", actor.getHeadshotPath());
        verify(actorRepository).save(actor);

        Optional<Resource> loaded = service.loadHeadshot(7);
        assertTrue(loaded.isPresent());
        assertEquals(4, loaded.get().contentLength());
        assertEquals(Optional.of("image/png"), service.getContentType(7));
        assertTrue(service.hasHeadshot(actor));
    }

    @Test
    void replacingHeadshotKeepsSingleRowAndUpdatesType() {
        Actor actor = actor(7);
        service.updateHeadshot(actor, png(new byte[] {1}), false);
        service.updateHeadshot(actor,
                new MockMultipartFile("headshot", "photo.jpg", "image/jpeg", new byte[] {9, 9}), false);

        assertEquals("headshots/7.jpg", actor.getHeadshotPath());
        assertEquals(Optional.of("image/jpeg"), service.getContentType(7));
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM actor_headshot WHERE actor_id = 7", Integer.class);
        assertEquals(1, rows);
    }

    @Test
    void removeHeadshotDeletesRowAndClearsPath() {
        Actor actor = actor(7);
        service.updateHeadshot(actor, png(new byte[] {1}), false);

        service.updateHeadshot(actor, null, true);

        assertEquals(null, actor.getHeadshotPath());
        assertTrue(service.loadHeadshot(7).isEmpty());
        assertFalse(service.hasHeadshot(actor));
    }

    @Test
    void rejectsDisallowedContentType() {
        Actor actor = actor(7);
        MockMultipartFile pdf = new MockMultipartFile("headshot", "cv.pdf", "application/pdf", new byte[] {1});

        assertThrows(IllegalArgumentException.class, () -> service.updateHeadshot(actor, pdf, false));
        assertTrue(service.loadHeadshot(7).isEmpty());
        verify(actorRepository, never()).save(actor);
    }

    @Test
    void rejectsOversizedFile() {
        service = new ActorHeadshotServiceImpl(actorRepository, jdbcTemplate, 3);
        Actor actor = actor(7);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateHeadshot(actor, png(new byte[] {1, 2, 3, 4}), false));
        assertTrue(service.loadHeadshot(7).isEmpty());
    }

    @Test
    void missingHeadshotLoadsEmpty() {
        assertTrue(service.loadHeadshot(999).isEmpty());
        assertTrue(service.getContentType(999).isEmpty());
        assertTrue(service.loadHeadshot(null).isEmpty());
    }
}
