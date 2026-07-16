package com.scripty.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.scripty.dto.Project;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders lyrics as a readable document: each song is a title followed by its
 * lines, one line per paragraph. Blank lines in the lyrics are preserved, since
 * they are how writers separate verses from choruses.
 */
@Service
public class SongExportServiceImpl implements SongExportService {

    private static final String UNTITLED = "Untitled Song";
    // The export buttons are hidden when a project has no songs, but the URL is
    // still reachable; an empty file would look like a broken download.
    private static final String EMPTY_PLACEHOLDER = "No songs yet.";

    // Body text, not screenplay text: proportional font, generous 1in margins.
    private static final float PDF_MARGIN = 72f; // 1in
    private static final Font PDF_TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f);
    private static final Font PDF_BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 12f);

    private static final String DOCX_FONT = "Calibri";
    private static final int DOCX_TITLE_HALF_POINTS = 32; // 16pt
    private static final int DOCX_BODY_HALF_POINTS = 24;  // 12pt

    @Autowired
    private TextDocumentRepository textDocumentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectService projectService;

    @Override
    @Transactional(readOnly = true)
    public SongExport exportSong(Integer documentId, Format format, User currentUser) {
        TextDocument doc = textDocumentRepository.findById(documentId).orElse(null);
        if (doc == null || doc.getProject() == null
                || !TextDocument.TYPE_SONG.equalsIgnoreCase(doc.getDocumentType())) {
            return null;
        }
        if (!projectService.canUserAccessProject(doc.getProject().getId(), currentUser)) {
            return null;
        }
        return render(List.of(doc), title(doc), format);
    }

    @Override
    @Transactional(readOnly = true)
    public SongExport exportAllSongs(Integer projectId, Format format, User currentUser) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || !projectService.canUserAccessProject(projectId, currentUser)) {
            return null;
        }
        List<TextDocument> songs = new ArrayList<>();
        for (TextDocument doc : textDocumentRepository
                .findByProjectIdOrderBySortOrderAscUpdatedAtDesc(projectId)) {
            if (TextDocument.TYPE_SONG.equalsIgnoreCase(doc.getDocumentType())) {
                songs.add(doc);
            }
        }
        String base = project.getTitle() != null && !project.getTitle().isBlank()
                ? project.getTitle() + " - Songs"
                : "Songs";
        return render(songs, base, format);
    }

    private SongExport render(List<TextDocument> songs, String baseName, Format format) {
        byte[] content = switch (format) {
            case PDF -> renderPdf(songs);
            case DOCX -> renderDocx(songs);
            case TXT -> renderTxt(songs).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        };
        if (content == null) {
            return null;
        }
        return new SongExport(filename(baseName, format.extension()), format.contentType(), content);
    }

    private String renderTxt(List<TextDocument> songs) {
        if (songs.isEmpty()) {
            return EMPTY_PLACEHOLDER + "\n";
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

    private byte[] renderPdf(List<TextDocument> songs) {
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
                document.add(new Paragraph(EMPTY_PLACEHOLDER, PDF_BODY_FONT));
            }

            document.close();
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] renderDocx(List<TextDocument> songs) {
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
                run.setText(EMPTY_PLACEHOLDER);
            }

            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static String title(TextDocument song) {
        return song.getTitle() != null && !song.getTitle().isBlank() ? song.getTitle().trim() : UNTITLED;
    }

    private static String lyrics(TextDocument song) {
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
