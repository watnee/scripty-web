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
import com.scripty.dto.SongBlock;
import com.scripty.dto.SongEdition;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.SongBlockRepository;
import com.scripty.repository.TextDocumentRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SongExportServiceImplTest {

    private static final Integer PROJECT_ID = 7;

    private TextDocumentRepository textDocumentRepository;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private SongEditionService songEditionService;
    private SongBlockRepository songBlockRepository;
    private SongExportServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        textDocumentRepository = mock(TextDocumentRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        songEditionService = mock(SongEditionService.class);
        songBlockRepository = mock(SongBlockRepository.class);
        user = new User();

        service = new SongExportServiceImpl();
        ReflectionTestUtils.setField(service, "textDocumentRepository", textDocumentRepository);
        ReflectionTestUtils.setField(service, "projectRepository", projectRepository);
        ReflectionTestUtils.setField(service, "projectService", projectService);
        ReflectionTestUtils.setField(service, "songEditionService", songEditionService);
        ReflectionTestUtils.setField(service, "songBlockRepository", songBlockRepository);

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

    private SongBlock songBlock(String content) {
        SongBlock block = new SongBlock();
        block.setContent(content);
        return block;
    }

    @Test
    void exportsTheActiveVersionsBlocksNotTheStalePublishedCache() {
        // The cached content field is only rebuilt for the published version, so
        // an unpublished active version used to export the wrong lyrics silently.
        TextDocument doc = song(1, "Hold The Line", "STALE PUBLISHED LYRICS");
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(doc));

        SongEdition draft = new SongEdition();
        draft.setId(42);
        draft.setPublished(false);
        when(songEditionService.getDefaultForDocument(1)).thenReturn(draft);
        when(songBlockRepository.findBySongEditionIdAndDeletedAtIsNullOrderByOrderAsc(42))
                .thenReturn(List.of(songBlock("Draft verse one"), songBlock("Draft verse two")));

        String body = new String(
                service.exportSong(1, SongExportService.Format.TXT, user).content(),
                StandardCharsets.UTF_8);

        assertTrue(body.contains("Draft verse one"), "active version lyrics missing; got:\n" + body);
        assertTrue(body.contains("Draft verse two"), "active version lyrics missing; got:\n" + body);
        assertFalse(body.contains("STALE PUBLISHED LYRICS"), "exported the stale cache; got:\n" + body);
    }

    @Test
    void fallsBackToCachedContentForLegacySongsWithoutBlocks() {
        TextDocument doc = song(1, "Hold The Line", "Legacy lyrics");
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(doc));

        SongEdition edition = new SongEdition();
        edition.setId(42);
        when(songEditionService.getDefaultForDocument(1)).thenReturn(edition);
        when(songBlockRepository.findBySongEditionIdAndDeletedAtIsNullOrderByOrderAsc(42))
                .thenReturn(List.of());

        String body = new String(
                service.exportSong(1, SongExportService.Format.TXT, user).content(),
                StandardCharsets.UTF_8);

        assertTrue(body.contains("Legacy lyrics"), "legacy fallback missing; got:\n" + body);
    }

    @Test
    void exportsSingleSongAsTextWithTitleAndLyrics() {
        TextDocument doc = song(1, "Hold The Line", "First verse\n\nSecond verse");
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(doc));

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
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(doc));

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
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(doc));

        assertNull(service.exportSong(1, SongExportService.Format.TXT, user));
    }

    @Test
    void exportSongRejectsUnknownIdAndInaccessibleProject() {
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(99)).thenReturn(Optional.empty());
        assertNull(service.exportSong(99, SongExportService.Format.TXT, user));

        TextDocument doc = song(1, "Locked", "body");
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(doc));
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
        when(textDocumentRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(PROJECT_ID))
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
    void exportSongsIncludesOnlyTheSelectedSongs() {
        TextDocument first = song(1, "Opener", "one");
        TextDocument second = song(3, "Closer", "two");

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project("My Musical")));
        when(textDocumentRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(PROJECT_ID))
                .thenReturn(List.of(first, second));

        SongExportService.SongExport export =
                service.exportSongs(PROJECT_ID, List.of(3), SongExportService.Format.TXT, user);

        assertNotNull(export);
        String body = new String(export.content(), StandardCharsets.UTF_8);
        assertTrue(body.contains("Closer"));
        assertFalse(body.contains("Opener"));
        // One selected song names the file after itself.
        assertEquals("Closer.txt", export.filename());
    }

    @Test
    void selectingSeveralSongsKeepsListOrderAndProjectFilename() {
        TextDocument first = song(1, "Opener", "one");
        TextDocument middle = song(2, "Middle", "skipped");
        TextDocument last = song(3, "Closer", "two");

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project("My Musical")));
        when(textDocumentRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(PROJECT_ID))
                .thenReturn(List.of(first, middle, last));

        // Ids are passed newest-first; list order should still win.
        SongExportService.SongExport export =
                service.exportSongs(PROJECT_ID, List.of(3, 1), SongExportService.Format.TXT, user);

        assertNotNull(export);
        assertEquals("My-Musical-Songs.txt", export.filename());
        String body = new String(export.content(), StandardCharsets.UTF_8);
        assertTrue(body.indexOf("Opener") < body.indexOf("Closer"));
        assertFalse(body.contains("skipped"));
    }

    @Test
    void songIdsFromOtherProjectsAreIgnoredNotExported() {
        TextDocument mine = song(1, "Mine", "my lyrics");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project("Demo")));
        when(textDocumentRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(PROJECT_ID))
                .thenReturn(List.of(mine));

        // 404 rather than an empty document: the ids matched nothing here.
        assertNull(service.exportSongs(PROJECT_ID, List.of(999), SongExportService.Format.TXT, user));

        // A foreign id alongside a real one must not smuggle anything in.
        SongExportService.SongExport export =
                service.exportSongs(PROJECT_ID, List.of(1, 999), SongExportService.Format.TXT, user);
        assertNotNull(export);
        assertTrue(new String(export.content(), StandardCharsets.UTF_8).contains("my lyrics"));
    }

    @Test
    void emptyIdListMeansEverySong() {
        TextDocument first = song(1, "Opener", "one");
        TextDocument second = song(3, "Closer", "two");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project("Demo")));
        when(textDocumentRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(PROJECT_ID))
                .thenReturn(List.of(first, second));

        SongExportService.SongExport export =
                service.exportSongs(PROJECT_ID, List.of(), SongExportService.Format.TXT, user);

        assertNotNull(export);
        String body = new String(export.content(), StandardCharsets.UTF_8);
        assertTrue(body.contains("Opener"));
        assertTrue(body.contains("Closer"));
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
        when(textDocumentRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(PROJECT_ID))
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
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(doc));

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
    void exportsASongAsAnEpubBook() throws Exception {
        Project project = project("My Musical");
        project.setWriters("Written by\nJane Doe");
        TextDocument doc = song(1, "Hold The Line", "First verse\n\nSecond verse");
        doc.setProject(project);
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(doc));

        SongExportService.SongExport export =
                service.exportSong(1, SongExportService.Format.EPUB, user);

        assertNotNull(export);
        assertEquals("Hold-The-Line.epub", export.filename());
        assertEquals("application/epub+zip", export.contentType());

        Map<String, String> entries = unzip(export.content());
        assertEquals("application/epub+zip", entries.get("mimetype"));
        assertTrue(entries.containsKey("META-INF/container.xml"));
        assertTrue(entries.containsKey("OEBPS/content.opf"));
        assertTrue(entries.containsKey("OEBPS/nav.xhtml"));

        String opf = entries.get("OEBPS/content.opf");
        assertTrue(opf.contains("<dc:title>Hold The Line</dc:title>"));
        // The "Written by" credit line is not the author; the name below it is.
        assertTrue(opf.contains("<dc:creator>Jane Doe</dc:creator>"));

        String song = entries.get("OEBPS/song-1.xhtml");
        assertTrue(song.contains("<h1 class=\"song-title\">Hold The Line</h1>"));
        // Each verse is its own paragraph rather than a blank line inside one.
        assertTrue(song.contains("<p class=\"stanza\">First verse</p>"));
        assertTrue(song.contains("<p class=\"stanza\">Second verse</p>"));
    }

    @Test
    void epubGivesEachSongItsOwnChapterAndTocEntry() throws Exception {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project("My Musical")));
        when(textDocumentRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(PROJECT_ID))
                .thenReturn(List.of(song(1, "Opener", "one"), song(3, "Closer", "two")));

        SongExportService.SongExport export =
                service.exportAllSongs(PROJECT_ID, SongExportService.Format.EPUB, user);

        assertNotNull(export);
        assertEquals("My-Musical-Songs.epub", export.filename());

        Map<String, String> entries = unzip(export.content());
        assertTrue(entries.get("OEBPS/song-1.xhtml").contains("one"));
        assertTrue(entries.get("OEBPS/song-2.xhtml").contains("two"));

        String nav = entries.get("OEBPS/nav.xhtml");
        assertTrue(nav.contains("<a href=\"song-1.xhtml\">Opener</a>"));
        assertTrue(nav.contains("<a href=\"song-2.xhtml\">Closer</a>"));
        assertTrue(nav.indexOf("Opener") < nav.indexOf("Closer"));
    }

    /** Lyrics exported to EPUB and imported back must keep their lines and verse breaks. */
    @Test
    void epubRoundTripsLyricsThroughImport() throws Exception {
        TextDocument doc = song(1, "Hold The Line", "First line\nSecond line\n\nSecond verse");
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(doc));

        SongExportService.SongExport export =
                service.exportSong(1, SongExportService.Format.EPUB, user);

        String plain = EpubToFountainConverter.convertPlain(new ByteArrayInputStream(export.content()));

        assertEquals("Hold The Line\n\nFirst line\nSecond line\n\nSecond verse", plain);
    }

    @Test
    void epubEscapesXmlSpecialCharacters() throws Exception {
        TextDocument doc = song(1, "Tom & Jerry", "They <say> \"hi\"");
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(doc));

        SongExportService.SongExport export =
                service.exportSong(1, SongExportService.Format.EPUB, user);

        String song = unzip(export.content()).get("OEBPS/song-1.xhtml");
        assertTrue(song.contains("Tom &amp; Jerry"));
        assertTrue(song.contains("They &lt;say&gt; &quot;hi&quot;"));
    }

    private static Map<String, String> unzip(byte[] archive) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    @Test
    void parseFormatFallsBackToText() {
        assertEquals(SongExportService.Format.PDF, SongExportService.parseFormat("pdf"));
        assertEquals(SongExportService.Format.PDF, SongExportService.parseFormat("  PDF "));
        assertEquals(SongExportService.Format.DOCX, SongExportService.parseFormat("word"));
        assertEquals(SongExportService.Format.EPUB, SongExportService.parseFormat("epub"));
        assertEquals(SongExportService.Format.TXT, SongExportService.parseFormat("txt"));
        assertEquals(SongExportService.Format.TXT, SongExportService.parseFormat("nonsense"));
        assertEquals(SongExportService.Format.TXT, SongExportService.parseFormat(null));
    }
}
