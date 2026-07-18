package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScriptEditionService scriptEditionService;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId) {
        return exportProject(projectId, null, CapitalizationPreferences.ALL);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId, Integer editionId) {
        return exportProject(projectId, editionId, CapitalizationPreferences.ALL);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId, Integer editionId, CapitalizationPreferences caps) {
        Project project = projectRepository.findById(projectId).orElse(null);
        ScriptEdition edition = scriptEditionService.requireForProject(projectId, editionId);
        List<Block> blocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAscIdAsc(projectId);
        try {
            return toEpub(project, blocks, projectId, caps != null ? caps : CapitalizationPreferences.ALL);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export project " + projectId + " as EPUB", e);
        }
    }

    /** A spine document: the scene/section it opens with, plus the blocks it holds. */
    private record Chapter(String title, String href, List<Block> blocks) {
    }

    /** Package-visible for unit tests. */
    static byte[] toEpub(Project project, List<Block> blocks, Integer projectId) throws IOException {
        return toEpub(project, blocks, projectId, CapitalizationPreferences.ALL);
    }

    /** Package-visible for unit tests. */
    static byte[] toEpub(Project project, List<Block> blocks, Integer projectId, CapitalizationPreferences caps)
            throws IOException {
        String title = bookTitle(project);
        List<Chapter> chapters = splitIntoChapters(blocks);

        List<EpubPackage.Document> documents = new ArrayList<>();
        if (hasTitlePage(project)) {
            documents.add(new EpubPackage.Document(
                    "title", "title.xhtml", "Title page", titlePageBody(project, title)));
        }
        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            documents.add(new EpubPackage.Document(
                    "chapter-" + (i + 1), chapter.href(), chapter.title(), chapterBody(chapter, caps)));
        }

        return EpubPackage.zip(
                new EpubPackage.Metadata(title, bookAuthor(project), "scripty-project-" + projectId),
                styleCss(caps),
                documents);
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

    private static String chapterBody(Chapter chapter, CapitalizationPreferences caps) {
        StringBuilder body = new StringBuilder();
        for (Block block : chapter.blocks()) {
            appendBlock(body, block, caps);
        }
        if (body.isEmpty()) {
            body.append("    <p class=\"action\"></p>\n");
        }
        return "    <section epub:type=\"chapter\">\n" + body + "    </section>\n";
    }

    private static void appendBlock(StringBuilder body, Block block, CapitalizationPreferences caps) {
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
            content = caps.apply(name.trim(), type);
        }
        if (Block.TYPE_PARENTHETICAL.equals(type)) {
            String paren = content.trim();
            content = paren.startsWith("(") && paren.endsWith(")") ? paren : "(" + content + ")";
        }

        body.append("      <p class=\"").append(cssClass).append("\">")
                .append(EpubPackage.inlineText(content))
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

    private static boolean hasTitlePage(Project project) {
        return project != null && (notBlank(project.getScreenplayTitle())
                || notBlank(project.getTitle())
                || notBlank(project.getWriters())
                || notBlank(project.getContactInfo())
                || notBlank(project.getScreenplayVersion()));
    }

    private static String titlePageBody(Project project, String title) {
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
        return body.toString();
    }

    /**
     * EPUB is reflowable: the reading system owns page size, margins and font
     * size, and readers legitimately scale text. So this deliberately does not
     * pin 12pt / line-height 1 the way the PDF and DOCX exports do — that would
     * fight the reader and hurt accessibility for no gain.
     *
     * <p>What it does match is the <em>proportions</em>. The indents below are
     * the ScreenplayLayout measurements expressed as percentages of the 6in text
     * column (2.2in / 6in = 36.667%, and so on), which is exactly how the
     * on-screen page view states them in scripty.css. The vertical rhythm mirrors
     * it too: a 1em gap before each element, 2em before a scene heading, and
     * none inside a speech group.
     *
     * <p>Capitalization stays caller-driven: character cues are already
     * uppercased in the markup above when enabled, so their rule only needs the
     * transform as a belt-and-braces measure; scene/transition/shot rely on it
     * entirely, and each is omitted when the user has turned that element off.
     */
    private static String styleCss(CapitalizationPreferences caps) {
        String sceneCaps = caps.scene() ? " text-transform: uppercase;" : "";
        String characterCaps = caps.character() ? " text-transform: uppercase;" : "";
        String transitionCaps = caps.transition() ? " text-transform: uppercase;" : "";
        String shotCaps = caps.shot() ? " text-transform: uppercase;" : "";
        return """
                body { font-family: "Courier Prime", "Courier New", monospace; margin: 1em; }
                p { margin: 1em 0 0 0; white-space: pre-wrap; }
                .scene { font-weight: bold;%s margin-top: 2em; }
                .action { }
                .character { margin-left: 36.667%%;%s }
                .dual-dialogue { margin-left: 36.667%%;%s }
                .parenthetical { margin-top: 0; margin-left: 25%%; max-width: 33.333%%; font-style: italic; }
                .dialogue { margin-top: 0; margin-left: 16.667%%; max-width: 58.333%%; }
                .transition { text-align: right;%s }
                .shot {%s }
                .lyrics { font-style: italic; margin-left: 10%%; }
                .centered { text-align: center; }
                .section { font-weight: bold; }
                .synopsis { font-style: italic; color: #555; }
                .note { color: #777; }
                hr.page-break { page-break-after: always; border: 0; }
                .bold { font-weight: bold; }
                .italic { font-style: italic; }
                .underline { text-decoration: underline; }
                .title-page { text-align: center; margin-top: 25%%; }
                .title-page .screenplay-title { text-transform: uppercase; }
                .title-page .contact { margin-top: 2em; }
                """.formatted(sceneCaps, characterCaps, characterCaps, transitionCaps, shotCaps);
    }

    private static String bookTitle(Project project) {
        if (project == null) {
            return "Untitled";
        }
        String title = firstNonBlank(project.getScreenplayTitle(), project.getTitle());
        return title != null ? title.trim() : "Untitled";
    }

    private static String bookAuthor(Project project) {
        return project == null ? null : EpubPackage.authorFromWriters(project.getWriters());
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
        return EpubPackage.notBlank(value);
    }

    private static String escape(String text) {
        return EpubPackage.escape(text);
    }
}
