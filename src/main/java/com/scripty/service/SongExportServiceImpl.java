package com.scripty.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.scripty.dto.Project;
import com.scripty.dto.SongBlock;
import com.scripty.dto.SongEdition;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.SongBlockRepository;
import com.scripty.repository.TextDocumentRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders a song or a note as a readable document: a title followed by its
 * lines, one line per paragraph. Blank lines are preserved, since they are how
 * writers separate verses from choruses — and paragraphs from each other.
 *
 * <p>One renderer serves both kinds because there was never anything
 * song-shaped about it. It lays out a title and lines; {@code lyrics} answers
 * with a song's blocks or a note's content — it already fell back to the
 * document's own text for songs with no blocks, which is exactly what a note
 * is — and every format below has only ever seen the result. The one exception
 * is MusicXML, which is a score: notes are refused it rather than handed an
 * empty stave.
 */
@Service
public class SongExportServiceImpl implements SongExportService {

    private static final String UNTITLED_SONG = "Untitled Song";
    private static final String UNTITLED_NOTE = "Untitled Notes";
    // The export buttons are hidden when a project has nothing of the kind, but
    // the URL is still reachable; an empty file would look like a broken
    // download.
    private static final String EMPTY_SONGS = "No songs yet.";
    private static final String EMPTY_NOTES = "No notes yet.";

    // Body text, not screenplay text: proportional font, generous 1in margins.
    private static final float PDF_MARGIN = 72f; // 1in
    private static final Font PDF_TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f);
    private static final Font PDF_BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 12f);

    // Lyrics are read, not performed from a page: let the reader pick the font, and lean on
    // stanza spacing rather than the fixed metrics the screenplay EPUB needs.
    private static final String EPUB_CSS = """
            body { margin: 1em; }
            .song-title { font-size: 1.3em; margin: 0 0 1em 0; }
            .stanza { margin: 0 0 1.5em 0; }
            """;

    private static final String DOCX_FONT = "Calibri";
    private static final int DOCX_TITLE_HALF_POINTS = 32; // 16pt
    private static final int DOCX_BODY_HALF_POINTS = 24;  // 12pt

    @Autowired
    private TextDocumentRepository textDocumentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private SongEditionService songEditionService;

    @Autowired
    private SongBlockRepository songBlockRepository;

    @Override
    @Transactional(readOnly = true)
    public SongExport exportSong(Integer documentId, Format format, User currentUser) {
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null || doc.getProject() == null) {
            return null;
        }
        boolean song = isSong(doc.getDocumentType());
        // A score of a note is the one thing this cannot render. Everything
        // else here lays out a title and lines, which a note has as much as a
        // song does.
        if (!song && format == Format.MUSICXML) {
            return null;
        }
        if (!projectService.canUserAccessProject(doc.getProject().getId(), currentUser)) {
            return null;
        }
        return render(List.of(doc), title(doc), doc.getProject(),
                "scripty-" + (song ? "song-" : "note-") + doc.getId(), format,
                doc.getDocumentType());
    }

    @Override
    @Transactional(readOnly = true)
    public SongExport exportDocuments(Integer projectId, List<Integer> ids, String documentType,
                                      Format format, User currentUser) {
        // Null means songs: that is what every caller meant back when a
        // collection export could only ever be a songbook.
        boolean song = documentType == null || isSong(documentType);
        if (!song && format == Format.MUSICXML) {
            return null;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || !projectService.canUserAccessProject(projectId, currentUser)) {
            return null;
        }
        boolean filtered = ids != null && !ids.isEmpty();
        Set<Integer> wanted = filtered ? new HashSet<>(ids) : null;

        // Always start from this project's own documents, so ids the caller
        // supplied can only ever narrow the result, never widen it.
        List<TextDocument> documents = new ArrayList<>();
        for (TextDocument doc : textDocumentRepository
                .findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(projectId)) {
            if (isSong(doc.getDocumentType()) != song) {
                continue;
            }
            // A collection of "everything" means everything in the list, so
            // archived documents stay out of it — but naming one by id still
            // exports it, which is what the archive view's own export does.
            if (wanted == null && doc.isArchived()) {
                continue;
            }
            if (wanted == null || wanted.contains(doc.getId())) {
                documents.add(doc);
            }
        }
        // Asking for specific documents and matching none is a bad request, not
        // an empty songbook.
        if (filtered && documents.isEmpty()) {
            return null;
        }
        return render(documents, baseName(project, documents, filtered, song), project,
                "scripty-project-" + projectId + (song ? "-songs" : "-notes"), format,
                documentType);
    }

    /** A single selected document names the file after itself, matching a one-document export. */
    private static String baseName(Project project, List<TextDocument> documents, boolean filtered,
                                   boolean song) {
        if (filtered && documents.size() == 1) {
            return title(documents.get(0));
        }
        String kind = song ? "Songs" : "Notes";
        return project.getTitle() != null && !project.getTitle().isBlank()
                ? project.getTitle() + " - " + kind
                : kind;
    }

    private SongExport render(List<TextDocument> documents, String baseName, Project project,
                              String identifierSeed, Format format, String documentType) {
        // Only ever read where the list came back empty, which is the one place
        // a file has to say what it would have held.
        String empty = isSong(documentType) ? EMPTY_SONGS : EMPTY_NOTES;
        byte[] content = switch (format) {
            case PDF -> renderPdf(documents, empty);
            case DOCX -> renderDocx(documents, empty);
            case EPUB -> renderEpub(documents, baseName, project, identifierSeed, empty);
            case MUSICXML -> renderMusicXml(documents, baseName);
            case TXT -> renderTxt(documents, empty).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        };
        if (content == null) {
            return null;
        }
        return new SongExport(filename(baseName, format.extension()), format.contentType(), content);
    }

    /** Null counts as a song, matching the fallback the rest of the app makes. */
    private static boolean isSong(String documentType) {
        return documentType == null || TextDocument.TYPE_SONG.equalsIgnoreCase(documentType);
    }

    private String renderTxt(List<TextDocument> songs, String emptyPlaceholder) {
        if (songs.isEmpty()) {
            return emptyPlaceholder + "\n";
        }
        StringBuilder out = new StringBuilder();
        for (TextDocument song : songs) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            String heading = title(song);
            out.append(heading).append('\n');
            out.append("=".repeat(heading.length())).append("\n\n");
            out.append(lyrics(song));
            out.append('\n');
        }
        return out.toString();
    }

    private byte[] renderPdf(List<TextDocument> songs, String emptyPlaceholder) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.LETTER, PDF_MARGIN, PDF_MARGIN, PDF_MARGIN, PDF_MARGIN);
            PdfWriter.getInstance(document, out);
            document.open();

            boolean first = true;
            for (TextDocument song : songs) {
                // Each song starts its own page so a multi-song export stays readable.
                if (!first) {
                    document.newPage();
                }
                first = false;

                Paragraph heading = new Paragraph(title(song), PDF_TITLE_FONT);
                heading.setSpacingAfter(12f);
                document.add(heading);

                for (String line : lyrics(song).split("\n", -1)) {
                    // An empty Paragraph collapses; a space keeps the verse break visible.
                    Paragraph para = new Paragraph(line.isEmpty() ? " " : line, PDF_BODY_FONT);
                    para.setAlignment(Element.ALIGN_LEFT);
                    document.add(para);
                }
            }

            if (songs.isEmpty()) {
                document.add(new Paragraph(emptyPlaceholder, PDF_BODY_FONT));
            }

            document.close();
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] renderDocx(List<TextDocument> songs, String emptyPlaceholder) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            boolean first = true;
            for (TextDocument song : songs) {
                if (!first) {
                    XWPFParagraph breakPara = document.createParagraph();
                    XWPFRun breakRun = breakPara.createRun();
                    breakRun.addBreak(BreakType.PAGE);
                }
                first = false;

                XWPFParagraph heading = document.createParagraph();
                heading.setAlignment(ParagraphAlignment.LEFT);
                heading.setSpacingAfter(240); // 12pt in twips
                XWPFRun headingRun = heading.createRun();
                headingRun.setFontFamily(DOCX_FONT);
                headingRun.setFontSize(DOCX_TITLE_HALF_POINTS / 2);
                headingRun.setBold(true);
                headingRun.setText(title(song));

                for (String line : lyrics(song).split("\n", -1)) {
                    XWPFParagraph para = document.createParagraph();
                    para.setAlignment(ParagraphAlignment.LEFT);
                    XWPFRun run = para.createRun();
                    run.setFontFamily(DOCX_FONT);
                    run.setFontSize(DOCX_BODY_HALF_POINTS / 2);
                    run.setText(line);
                }
            }

            if (songs.isEmpty()) {
                XWPFRun run = document.createParagraph().createRun();
                run.setFontFamily(DOCX_FONT);
                run.setFontSize(DOCX_BODY_HALF_POINTS / 2);
                run.setText(emptyPlaceholder);
            }

            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A reflowable EPUB 3 songbook: one spine document per song, so readers offer a table of
     * contents and each song opens on its own screen. Stanzas become paragraphs, keeping the verse
     * breaks the writer typed as structure rather than as blank lines the reader might collapse.
     */
    private byte[] renderEpub(List<TextDocument> songs, String bookTitle, Project project,
                              String identifierSeed, String emptyPlaceholder) {
        List<EpubPackage.Document> documents = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            TextDocument song = songs.get(i);
            documents.add(new EpubPackage.Document(
                    "song-" + (i + 1), "song-" + (i + 1) + ".xhtml", title(song), songBody(song)));
        }
        if (documents.isEmpty()) {
            documents.add(new EpubPackage.Document("songs", "songs.xhtml", bookTitle,
                    "    <section epub:type=\"chapter\" class=\"song\">\n"
                            + "      <p class=\"stanza\">" + EpubPackage.escape(emptyPlaceholder) + "</p>\n"
                            + "    </section>\n"));
        }
        try {
            return EpubPackage.zip(
                    new EpubPackage.Metadata(bookTitle,
                            project != null ? EpubPackage.authorFromWriters(project.getWriters()) : null,
                            identifierSeed),
                    EPUB_CSS,
                    documents);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A score of the words alone, for setting to music elsewhere. Several songs
     * become sections of one score rather than several files, matching how the
     * other formats gather a songbook — MusicXML has no notion of a second
     * piece in the same document, so each song is a titled section on its own
     * page.
     */
    private byte[] renderMusicXml(List<TextDocument> songs, String scoreTitle) {
        if (songs.isEmpty()) {
            return SongMusicXmlWriter.write(scoreTitle, List.of());
        }
        if (songs.size() == 1) {
            // The score already carries the song's name as its title, so a
            // heading above the staff would only say it twice.
            return SongMusicXmlWriter.write(scoreTitle,
                    List.of(new SongMusicXmlWriter.Song(null, lyrics(songs.get(0)))));
        }
        List<SongMusicXmlWriter.Song> sections = new ArrayList<>();
        for (TextDocument song : songs) {
            sections.add(new SongMusicXmlWriter.Song(title(song), lyrics(song)));
        }
        return SongMusicXmlWriter.write(scoreTitle, sections);
    }

    private String songBody(TextDocument song) {
        StringBuilder body = new StringBuilder();
        body.append("    <section epub:type=\"chapter\" class=\"song\">\n");
        body.append("      <h1 class=\"song-title\">").append(EpubPackage.escape(title(song))).append("</h1>\n");
        for (String stanza : stanzas(lyrics(song))) {
            body.append("      <p class=\"stanza\">").append(EpubPackage.inlineText(stanza)).append("</p>\n");
        }
        body.append("    </section>\n");
        return body.toString();
    }

    /** Splits lyrics on blank lines, the way writers separate verses from choruses. */
    private static List<String> stanzas(String lyrics) {
        List<String> stanzas = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lyrics.split("\n", -1)) {
            if (line.isBlank()) {
                if (current.length() > 0) {
                    stanzas.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line.stripTrailing());
        }
        if (current.length() > 0) {
            stanzas.add(current.toString());
        }
        return stanzas;
    }

    /**
     * Falls back to the very name the lists draw for a document with no title
     * of its own, so a file exported from an untitled note is headed the same
     * words the writer saw beside it.
     */
    private static String title(TextDocument document) {
        if (document.getTitle() != null && !document.getTitle().isBlank()) {
            return document.getTitle().trim();
        }
        return isSong(document.getDocumentType()) ? UNTITLED_SONG : UNTITLED_NOTE;
    }

    /**
     * Renders the song version the user is actually working in, matching how
     * script export resolves the active edition. The cached
     * {@link TextDocument#getContent()} is only rebuilt for the published
     * version, so reading it exported stale lyrics — silently, and with no way
     * to tell from the file — whenever the active version was not the published
     * one. Falls back to the cached content for legacy songs that have no blocks.
     */
    private String lyrics(TextDocument song) {
        SongEdition edition = songEditionService.getDefaultForDocument(song.getId());
        if (edition != null) {
            List<SongBlock> blocks = songBlockRepository
                    .findBySongEditionIdAndDeletedAtIsNullOrderByOrderAsc(edition.getId());
            if (!blocks.isEmpty()) {
                return blocks.stream()
                        .map(b -> b.getContent() != null ? b.getContent() : "")
                        .collect(Collectors.joining("\n"));
            }
        }
        return song.getContent() == null ? "" : song.getContent();
    }

    private static String filename(String base, String extension) {
        String fallback = "songs." + extension;
        if (base == null || base.isBlank()) {
            return fallback;
        }
        String sanitized = base.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "-")
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-zA-Z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (sanitized.isBlank()) {
            return fallback;
        }
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80).replaceAll("[.-]+$", "");
        }
        return sanitized + "." + extension;
    }
}
