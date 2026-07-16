package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SongExportServiceImplTest {

    private static final Integer PROJECT_ID = 7;

    private TextDocumentRepository textDocumentRepository;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private SongExportServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        textDocumentRepository = mock(TextDocumentRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        user = new User();

        service = new SongExportServiceImpl();
        ReflectionTestUtils.setField(service, "textDocumentRepository", textDocumentRepository);
        ReflectionTestUtils.setField(service, "projectRepository", projectRepository);
        ReflectionTestUtils.setField(service, "projectService", projectService);

        when(projectService.canUserAccessProject(any(Integer.class), any())).thenReturn(true);
    }

    private Project project(String title) {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setTitle(title);
        return project;
    }

    private TextDocument song(Integer id, String title, String content) {
        TextDocument doc = new TextDocument();
        doc.setId(id);
        doc.setTitle(title);
        doc.setContent(content);
        doc.setDocumentType(TextDocument.TYPE_SONG);
        doc.setProject(project("Demo"));
        return doc;
    }

    @Test
    void exportsSingleSongAsTextWithTitleAndLyrics() {
        TextDocument doc = song(1, "Hold The Line", "First verse\n\nSecond verse");
        when(textDocumentRepository.findById(1)).thenReturn(Optional.of(doc));

        SongExportService.SongExport export =
                service.exportSong(1, SongExportService.Format.TXT, user);

        assertNotNull(export);
        assertEquals("Hold-The-Line.txt", export.filename());
        assertEquals("text/plain; charset=UTF-8", export.contentType());
        String body = new String(export.content(), StandardCharsets.UTF_8);
        assertTrue(body.contains("Hold The Line"));
        assertTrue(body.contains("First verse"));
        // The blank line between verses is the writer's structure; keep it.
        assertTrue(body.contains("First verse\n\nSecond verse"));
    }

    @Test
    void untitledSongStillGetsAHeadingAndFilename() {
        TextDocument doc = song(1, "  ", "La la la");
        when(textDocumentRepository.findById(1)).thenReturn(Optional.of(doc));

        SongExportService.SongExport export =
                service.exportSong(1, SongExportService.Format.TXT, user);

        assertNotNull(export);
        assertEquals("Untitled-Song.txt", export.filename());
        assertTrue(new String(export.content(), StandardCharsets.UTF_8).contains("Untitled Song"));
    }

    @Test
    void exportSongRejectsNotes() {
        TextDocument doc = song(1, "Not a song", "body");
        doc.setDocumentType(TextDocument.TYPE_NOTES);
        when(textDocumentRepository.findById(1)).thenReturn(Optional.of(doc));

        assertNull(service.exportSong(1, SongExportService.Format.TXT, user));
    }

    @Test
    void exportSongRejectsUnknownIdAndInaccessibleProject() {
        when(textDocumentRepository.findById(99)).thenReturn(Optional.empty());
        assertNull(service.exportSong(99, SongExportService.Format.TXT, user));

        TextDocument doc = song(1, "Locked", "body");
        when(textDocumentRepository.findById(1)).thenReturn(Optional.of(doc));
        when(projectService.canUserAccessProject(eq(PROJECT_ID), any())).thenReturn(false);

        assertNull(service.exportSong(1, SongExportService.Format.TXT, user));
    }

    @Test
    void exportAllSongsIncludesOnlySongsInListOrder() {
        TextDocument first = song(1, "Opener", "one");
        TextDocument note = song(2, "A Note", "ignored");
        note.setDocumentType(TextDocument.TYPE_NOTES);
        TextDocument second = song(3, "Closer", "two");

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project("My Musical")));
        when(textDocumentRepository.findByProjectIdOrderBySortOrderAscUpdatedAtDesc(PROJECT_ID))
                .thenReturn(List.of(first, note, second));

        SongExportService.SongExport export =
                service.exportAllSongs(PROJECT_ID, SongExportService.Format.TXT, user);

        assertNotNull(export);
        assertEquals("My-Musical-Songs.txt", export.filename());
        String body = new String(export.content(), StandardCharsets.UTF_8);
        assertTrue(body.indexOf("Opener") < body.indexOf("Closer"));
        assertTrue(body.contains("one"));
        assertTrue(body.contains("two"));
        assertFalse(body.contains("A Note"));
        assertFalse(body.contains("ignored"));
    }

    @Test
    void exportAllSongsRejectsUnknownOrInaccessibleProject() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());
        assertNull(service.exportAllSongs(PROJECT_ID, SongExportService.Format.TXT, user));

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project("Demo")));
        when(projectService.canUserAccessProject(eq(PROJECT_ID), any())).thenReturn(false);
        assertNull(service.exportAllSongs(PROJECT_ID, SongExportService.Format.TXT, user));
    }

    @Test
    void exportsEmptySongListWithoutFailing() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project("Empty")));
        when(textDocumentRepository.findByProjectIdOrderBySortOrderAscUpdatedAtDesc(PROJECT_ID))
                .thenReturn(List.of());

        for (SongExportService.Format format : SongExportService.Format.values()) {
            SongExportService.SongExport export = service.exportAllSongs(PROJECT_ID, format, user);
            assertNotNull(export, "expected an export for " + format);
            assertTrue(export.content().length > 0, "expected content for " + format);
        }
    }

    @Test
    void rendersBinaryFormatsWithTheirOwnSignatures() {
        TextDocument doc = song(1, "Song", "line one\n\nline two");
        when(textDocumentRepository.findById(1)).thenReturn(Optional.of(doc));

        SongExportService.SongExport pdf = service.exportSong(1, SongExportService.Format.PDF, user);
        assertNotNull(pdf);
        assertEquals("Song.pdf", pdf.filename());
        assertTrue(new String(pdf.content(), 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));

        SongExportService.SongExport docx = service.exportSong(1, SongExportService.Format.DOCX, user);
        assertNotNull(docx);
        assertEquals("Song.docx", docx.filename());
        // DOCX is a zip container.
        assertEquals('P', (char) docx.content()[0]);
        assertEquals('K', (char) docx.content()[1]);
    }

    @Test
    void parseFormatFallsBackToText() {
        assertEquals(SongExportService.Format.PDF, SongExportService.parseFormat("pdf"));
        assertEquals(SongExportService.Format.PDF, SongExportService.parseFormat("  PDF "));
        assertEquals(SongExportService.Format.DOCX, SongExportService.parseFormat("word"));
        assertEquals(SongExportService.Format.TXT, SongExportService.parseFormat("txt"));
        assertEquals(SongExportService.Format.TXT, SongExportService.parseFormat("nonsense"));
        assertEquals(SongExportService.Format.TXT, SongExportService.parseFormat(null));
    }
}
