package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports a project as a reflowable EPUB 3 book.
 *
 * <p>Each block becomes a {@code <p>} whose class is its lowercased block type, which is what
 * {@link EpubToFountainConverter} reads back on import to rebuild the script losslessly. Readers
 * that ignore the classes still get sensible screenplay formatting from {@code style.css}.
 */
@Service
public class EpubExportServiceImpl implements EpubExportService {

    private static final String MIMETYPE = "application/epub+zip";
    private static final String OEBPS = "OEBPS/";

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScriptEditionService scriptEditionService;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId) {
        return exportProject(projectId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId, Integer editionId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        ScriptEdition edition = scriptEditionService.requireForProject(projectId, editionId);
        List<Block> blocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAsc(projectId);
        try {
            return toEpub(project, blocks, projectId);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export project " + projectId + " as EPUB", e);
        }
    }

    /** A spine document: the scene/section it opens with, plus the blocks it holds. */
    private record Chapter(String title, String href, List<Block> blocks) {
    }

    /** Package-visible for unit tests. */
    static byte[] toEpub(Project project, List<Block> blocks, Integer projectId) throws IOException {
        String title = bookTitle(project);
        String author = bookAuthor(project);
        boolean hasTitlePage = hasTitlePage(project);
        List<Chapter> chapters = splitIntoChapters(blocks);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            // The mimetype entry must come first and be stored uncompressed (EPUB OCF spec).
            writeStored(zip, "mimetype", MIMETYPE.getBytes(StandardCharsets.US_ASCII));
            write(zip, "META-INF/container.xml", containerXml());
            write(zip, OEBPS + "style.css", styleCss());
            if (hasTitlePage) {
                write(zip, OEBPS + "title.xhtml", titlePageXhtml(project, title));
            }
            for (Chapter chapter : chapters) {
                write(zip, OEBPS + chapter.href(), chapterXhtml(chapter));
            }
            write(zip, OEBPS + "nav.xhtml", navXhtml(title, hasTitlePage, chapters));
            write(zip, OEBPS + "content.opf", contentOpf(title, author, projectId, hasTitlePage, chapters));
        }
        return out.toByteArray();
    }

    private static List<Chapter> splitIntoChapters(List<Block> blocks) {
        List<Chapter> chapters = new ArrayList<>();
        List<Block> current = new ArrayList<>();
        String currentTitle = null;

        for (Block block : blocks) {
            boolean isHeading = Block.TYPE_SCENE.equals(block.getType())
                    || Block.TYPE_SECTION.equals(block.getType());
            if (isHeading && !current.isEmpty()) {
                chapters.add(chapter(currentTitle, current, chapters.size()));
                current = new ArrayList<>();
                currentTitle = null;
            }
            if (isHeading && currentTitle == null) {
                currentTitle = text(block);
            }
            current.add(block);
        }
        if (!current.isEmpty()) {
            chapters.add(chapter(currentTitle, current, chapters.size()));
        }
        if (chapters.isEmpty()) {
            chapters.add(chapter(null, List.of(), 0));
        }
        return chapters;
    }

    private static Chapter chapter(String title, List<Block> blocks, int index) {
        String label = title != null && !title.isBlank() ? title.trim() : "Scene " + (index + 1);
        return new Chapter(label, "chapter-" + (index + 1) + ".xhtml", List.copyOf(blocks));
    }

    private static String chapterXhtml(Chapter chapter) {
        StringBuilder body = new StringBuilder();
        for (Block block : chapter.blocks()) {
            appendBlock(body, block);
        }
        if (body.isEmpty()) {
            body.append("    <p class=\"action\"></p>\n");
        }
        return xhtmlDocument(chapter.title(), "    <section epub:type=\"chapter\">\n"
                + body
                + "    </section>\n");
    }

    private static void appendBlock(StringBuilder body, Block block) {
        String type = block.getType() != null ? block.getType() : Block.TYPE_ACTION;

        if (Block.TYPE_PAGE_BREAK.equals(type)) {
            body.append("      <hr class=\"page-break\"/>\n");
            return;
        }

        String content = text(block);
        String cssClass = type.toLowerCase(Locale.ROOT).replace('_', '-');
        for (String modifier : emphasisClasses(block)) {
            cssClass += " " + modifier;
        }

        // Character cues carry the linked person's name; dual cues mark the second column.
        if (Block.TYPE_CHARACTER.equals(type) || Block.TYPE_DUAL_DIALOGUE.equals(type)) {
            String name = block.getPerson() != null ? block.getPerson().getName() : content;
            if (name == null || name.isBlank()) {
                return;
            }
            content = name.trim().toUpperCase(Locale.ROOT);
        }
        if (Block.TYPE_PARENTHETICAL.equals(type)) {
            String paren = content.trim();
            content = paren.startsWith("(") && paren.endsWith(")") ? paren : "(" + content + ")";
        }

        body.append("      <p class=\"").append(cssClass).append("\">")
                .append(inlineText(content))
                .append("</p>\n");
    }

    private static List<String> emphasisClasses(Block block) {
        List<String> classes = new ArrayList<>(3);
        if (block.isTextBold()) {
            classes.add("bold");
        }
        if (block.isTextItalic()) {
            classes.add("italic");
        }
        if (block.isTextUnderline()) {
            classes.add("underline");
        }
        return classes;
    }

    /** Escapes text and preserves hard line breaks inside a block. */
    private static String inlineText(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append("<br/>");
            }
            sb.append(escape(lines[i]));
        }
        return sb.toString();
    }

    private static boolean hasTitlePage(Project project) {
        return project != null && (notBlank(project.getScreenplayTitle())
                || notBlank(project.getTitle())
                || notBlank(project.getWriters())
                || notBlank(project.getContactInfo())
                || notBlank(project.getScreenplayVersion()));
    }

    private static String titlePageXhtml(Project project, String title) {
        StringBuilder body = new StringBuilder();
        body.append("    <section epub:type=\"titlepage\" class=\"title-page\">\n");
        body.append("      <h1 class=\"screenplay-title\">").append(escape(title)).append("</h1>\n");
        if (notBlank(project.getWriters())) {
            for (String line : project.getWriters().trim().split("\n", -1)) {
                if (!line.isBlank()) {
                    body.append("      <p class=\"writers\">").append(escape(line.trim())).append("</p>\n");
                }
            }
        }
        if (notBlank(project.getScreenplayVersion())) {
            body.append("      <p class=\"version\">")
                    .append(escape(project.getScreenplayVersion().trim())).append("</p>\n");
        }
        if (notBlank(project.getContactInfo())) {
            for (String line : project.getContactInfo().trim().split("\n", -1)) {
                if (!line.isBlank()) {
                    body.append("      <p class=\"contact\">").append(escape(line.trim())).append("</p>\n");
                }
            }
        }
        body.append("    </section>\n");
        return xhtmlDocument(title, body.toString());
    }

    private static String navXhtml(String title, boolean hasTitlePage, List<Chapter> chapters) {
        StringBuilder body = new StringBuilder();
        body.append("    <nav epub:type=\"toc\" id=\"toc\">\n");
        body.append("      <h1>Contents</h1>\n");
        body.append("      <ol>\n");
        if (hasTitlePage) {
            body.append("        <li><a href=\"title.xhtml\">Title page</a></li>\n");
        }
        for (Chapter chapter : chapters) {
            body.append("        <li><a href=\"").append(chapter.href()).append("\">")
                    .append(escape(chapter.title())).append("</a></li>\n");
        }
        body.append("      </ol>\n");
        body.append("    </nav>\n");
        return xhtmlDocument(title, body.toString());
    }

    private static String contentOpf(String title, String author, Integer projectId,
                                     boolean hasTitlePage, List<Chapter> chapters) {
        String modified = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String identifier = "urn:uuid:" + UUID.nameUUIDFromBytes(
                ("scripty-project-" + projectId).getBytes(StandardCharsets.UTF_8));

        StringBuilder opf = new StringBuilder();
        opf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        opf.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" ")
                .append("unique-identifier=\"book-id\" xml:lang=\"en\">\n");
        opf.append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
        opf.append("    <dc:identifier id=\"book-id\">").append(identifier).append("</dc:identifier>\n");
        opf.append("    <dc:title>").append(escape(title)).append("</dc:title>\n");
        opf.append("    <dc:language>en</dc:language>\n");
        if (notBlank(author)) {
            opf.append("    <dc:creator>").append(escape(author)).append("</dc:creator>\n");
        }
        opf.append("    <meta property=\"dcterms:modified\">").append(modified).append("</meta>\n");
        opf.append("  </metadata>\n");

        opf.append("  <manifest>\n");
        opf.append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" ")
                .append("properties=\"nav\"/>\n");
        opf.append("    <item id=\"style\" href=\"style.css\" media-type=\"text/css\"/>\n");
        if (hasTitlePage) {
            opf.append("    <item id=\"title\" href=\"title.xhtml\" media-type=\"application/xhtml+xml\"/>\n");
        }
        for (int i = 0; i < chapters.size(); i++) {
            opf.append("    <item id=\"chapter-").append(i + 1).append("\" href=\"")
                    .append(chapters.get(i).href()).append("\" media-type=\"application/xhtml+xml\"/>\n");
        }
        opf.append("  </manifest>\n");

        opf.append("  <spine>\n");
        if (hasTitlePage) {
            opf.append("    <itemref idref=\"title\"/>\n");
        }
        for (int i = 0; i < chapters.size(); i++) {
            opf.append("    <itemref idref=\"chapter-").append(i + 1).append("\"/>\n");
        }
        opf.append("  </spine>\n");
        opf.append("</package>\n");
        return opf.toString();
    }

    private static String xhtmlDocument(String title, String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" "
                + "xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"en\" lang=\"en\">\n"
                + "  <head>\n"
                + "    <title>" + escape(title) + "</title>\n"
                + "    <link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/>\n"
                + "  </head>\n"
                + "  <body>\n"
                + body
                + "  </body>\n"
                + "</html>\n";
    }

    private static String containerXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<container version=\"1.0\" "
                + "xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
                + "  <rootfiles>\n"
                + "    <rootfile full-path=\"OEBPS/content.opf\" "
                + "media-type=\"application/oebps-package+xml\"/>\n"
                + "  </rootfiles>\n"
                + "</container>\n";
    }

    private static String styleCss() {
        return """
                body { font-family: "Courier Prime", "Courier New", monospace; margin: 1em; }
                p { margin: 0 0 1em 0; white-space: pre-wrap; }
                .scene { font-weight: bold; text-transform: uppercase; margin-top: 1.5em; }
                .action { }
                .character { margin: 0 0 0 20%; text-transform: uppercase; }
                .dual-dialogue { margin: 0 0 0 20%; text-transform: uppercase; }
                .parenthetical { margin: 0 0 0 15%; }
                .dialogue { margin: 0 0 1em 10%; }
                .transition { text-align: right; text-transform: uppercase; }
                .shot { text-transform: uppercase; }
                .lyrics { font-style: italic; margin-left: 10%; }
                .centered { text-align: center; }
                .section { font-weight: bold; }
                .synopsis { font-style: italic; color: #555; }
                .note { color: #777; }
                hr.page-break { page-break-after: always; border: 0; }
                .bold { font-weight: bold; }
                .italic { font-style: italic; }
                .underline { text-decoration: underline; }
                .title-page { text-align: center; margin-top: 25%; }
                .title-page .screenplay-title { text-transform: uppercase; }
                .title-page .contact { margin-top: 2em; }
                """;
    }

    private static String bookTitle(Project project) {
        if (project == null) {
            return "Untitled";
        }
        String title = firstNonBlank(project.getScreenplayTitle(), project.getTitle());
        return title != null ? title.trim() : "Untitled";
    }

    private static String bookAuthor(Project project) {
        if (project == null || !notBlank(project.getWriters())) {
            return null;
        }
        // The writers field may lead with a credit line ("Written by"); the author is the rest.
        String[] lines = project.getWriters().trim().split("\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.toLowerCase(Locale.ROOT).matches(
                    "^(?:written(?: and directed)? by|story by|screenplay by|by)\\.?$")) {
                return trimmed;
            }
        }
        return lines[0].trim();
    }

    private static String text(Block block) {
        return block.getContent() != null ? block.getContent() : "";
    }

    private static String firstNonBlank(String a, String b) {
        if (notBlank(a)) {
            return a;
        }
        return notBlank(b) ? b : null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void writeStored(ZipOutputStream zip, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        CRC32 crc = new CRC32();
        crc.update(content);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
        // Subsequent entries return to the default deflate method.
        zip.setMethod(ZipOutputStream.DEFLATED);
    }
}
