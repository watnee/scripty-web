package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class EpubExportServiceImplTest {

    @Test
    void producesAValidEpubContainer() throws Exception {
        byte[] epub = EpubExportServiceImpl.toEpub(project(), List.of(
                block(Block.TYPE_SCENE, "INT. KITCHEN - DAY", null)), 7);

        Map<String, String> entries = unzip(epub);
        assertEquals("application/epub+zip", entries.get("mimetype"));
        assertTrue(entries.containsKey("META-INF/container.xml"));
        assertTrue(entries.containsKey("OEBPS/content.opf"));
        assertTrue(entries.containsKey("OEBPS/nav.xhtml"));
        assertTrue(entries.containsKey("OEBPS/style.css"));

        String opf = entries.get("OEBPS/content.opf");
        assertTrue(opf.contains("<dc:title>My Script</dc:title>"));
        assertTrue(opf.contains("<dc:creator>Jane Doe</dc:creator>"));
        assertTrue(opf.contains("<dc:language>en</dc:language>"));
        assertTrue(opf.contains("dcterms:modified"));
        assertTrue(opf.contains("properties=\"nav\""));
        assertTrue(opf.contains("<itemref idref=\"title\"/>"));
        assertTrue(opf.contains("<itemref idref=\"chapter-1\"/>"));

        assertTrue(entries.get("META-INF/container.xml").contains("full-path=\"OEBPS/content.opf\""));
        assertTrue(entries.get("OEBPS/nav.xhtml").contains("epub:type=\"toc\""));
        assertTrue(entries.get("OEBPS/nav.xhtml").contains("INT. KITCHEN - DAY"));
    }

    /** The OCF spec requires mimetype to be the first entry and stored uncompressed. */
    @Test
    void storesMimetypeFirstAndUncompressed() throws Exception {
        byte[] epub = EpubExportServiceImpl.toEpub(new Project(), List.of(
                block(Block.TYPE_ACTION, "Jane enters.", null)), 1);

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(epub))) {
            ZipEntry first = zip.getNextEntry();
            assertEquals("mimetype", first.getName());
            assertEquals(ZipEntry.STORED, first.getMethod());
        }
    }

    @Test
    void startsANewChapterAtEachScene() throws Exception {
        byte[] epub = EpubExportServiceImpl.toEpub(new Project(), List.of(
                block(Block.TYPE_SCENE, "INT. KITCHEN - DAY", null),
                block(Block.TYPE_ACTION, "Jane enters.", null),
                block(Block.TYPE_SCENE, "EXT. GARDEN - NIGHT", null),
                block(Block.TYPE_ACTION, "Bob waits.", null)), 1);

        Map<String, String> entries = unzip(epub);
        assertTrue(entries.containsKey("OEBPS/chapter-1.xhtml"));
        assertTrue(entries.containsKey("OEBPS/chapter-2.xhtml"));
        assertTrue(entries.get("OEBPS/chapter-1.xhtml").contains("Jane enters."));
        assertTrue(entries.get("OEBPS/chapter-2.xhtml").contains("Bob waits."));
    }

    @Test
    void escapesXmlSpecialCharacters() throws Exception {
        byte[] epub = EpubExportServiceImpl.toEpub(new Project(), List.of(
                block(Block.TYPE_ACTION, "Tom & Jerry <say> \"hi\"", null)), 1);

        String chapter = unzip(epub).get("OEBPS/chapter-1.xhtml");
        assertTrue(chapter.contains("Tom &amp; Jerry &lt;say&gt; &quot;hi&quot;"));
    }

    @Test
    void roundTripsAScriptBackToFountain() throws Exception {
        Person jane = new Person();
        jane.setName("Jane");

        byte[] epub = EpubExportServiceImpl.toEpub(project(), List.of(
                block(Block.TYPE_SCENE, "INT. KITCHEN - DAY", null),
                block(Block.TYPE_ACTION, "Jane enters.", null),
                block(Block.TYPE_CHARACTER, "JANE", jane),
                block(Block.TYPE_PARENTHETICAL, "quietly", null),
                block(Block.TYPE_DIALOGUE, "Hello there.", jane),
                block(Block.TYPE_TRANSITION, "CUT TO:", null),
                block(Block.TYPE_LYRICS, "La la la", null),
                block(Block.TYPE_CENTERED, "THE END", null),
                block(Block.TYPE_PAGE_BREAK, "", null)), 1);

        String fountain = EpubToFountainConverter.convert(new ByteArrayInputStream(epub));

        assertTrue(fountain.contains("Title: My Script"));
        assertTrue(fountain.contains("Credit: written by"));
        assertTrue(fountain.contains("Author: Jane Doe"));
        assertTrue(fountain.contains("Draft date: Draft 1"));
        assertTrue(fountain.contains("Contact: jane@example.com"));
        assertTrue(fountain.contains("INT. KITCHEN - DAY"));
        assertTrue(fountain.contains("Jane enters."));
        assertTrue(fountain.contains("JANE"));
        assertTrue(fountain.contains("(quietly)"));
        assertTrue(fountain.contains("Hello there."));
        assertTrue(fountain.contains("CUT TO:"));
        assertTrue(fountain.contains("~La la la"));
        assertTrue(fountain.contains(">THE END<"));
        assertTrue(fountain.contains("==="));
    }

    @Test
    void roundTripsDualDialogueAndEmphasis() throws Exception {
        Person alice = new Person();
        alice.setName("Alice");
        Person bob = new Person();
        bob.setName("Bob");

        Block bold = block(Block.TYPE_ACTION, "Loud noise.", null);
        bold.setTextBold(true);
        bold.setTextItalic(true);

        byte[] epub = EpubExportServiceImpl.toEpub(new Project(), List.of(
                block(Block.TYPE_CHARACTER, "ALICE", alice),
                block(Block.TYPE_DIALOGUE, "Left side.", alice),
                block(Block.TYPE_DUAL_DIALOGUE, "BOB", bob),
                block(Block.TYPE_DIALOGUE, "Right side.", bob),
                bold), 1);

        String fountain = EpubToFountainConverter.convert(new ByteArrayInputStream(epub));

        assertTrue(fountain.contains("ALICE"));
        assertTrue(fountain.contains("BOB ^"));
        assertTrue(fountain.contains("Left side."));
        assertTrue(fountain.contains("Right side."));
        assertTrue(fountain.contains("***Loud noise.***"));
    }

    /** Multi-line dialogue survives as <br/> and comes back as real newlines. */
    @Test
    void roundTripsMultiLineDialogue() throws Exception {
        Person jane = new Person();
        jane.setName("Jane");

        byte[] epub = EpubExportServiceImpl.toEpub(new Project(), List.of(
                block(Block.TYPE_CHARACTER, "JANE", jane),
                block(Block.TYPE_DIALOGUE, "First line.\nSecond line.", jane)), 1);

        assertTrue(unzip(epub).get("OEBPS/chapter-1.xhtml").contains("First line.<br/>Second line."));

        String fountain = EpubToFountainConverter.convert(new ByteArrayInputStream(epub));
        assertTrue(fountain.contains("First line.\nSecond line."));
    }

    /** A scene heading that isn't INT./EXT. needs Fountain's forcing dot to survive re-import. */
    @Test
    void forcesAmbiguousElementsOnImport() throws Exception {
        byte[] epub = EpubExportServiceImpl.toEpub(new Project(), List.of(
                block(Block.TYPE_SCENE, "SOMEWHERE ELSE", null),
                block(Block.TYPE_ACTION, "MEANWHILE", null)), 1);

        String fountain = EpubToFountainConverter.convert(new ByteArrayInputStream(epub));

        assertTrue(fountain.contains(".SOMEWHERE ELSE"));
        // All-caps action would otherwise re-import as a character cue.
        assertTrue(fountain.contains("!MEANWHILE"));
    }

    @Test
    void importsAThirdPartyEpubAsPlainParagraphs() throws Exception {
        byte[] epub = thirdPartyEpub();

        String fountain = EpubToFountainConverter.convert(new ByteArrayInputStream(epub));

        assertTrue(fountain.contains("#Chapter One"));
        assertTrue(fountain.contains("It was a dark and stormy night."));
        // Named entities and DOCTYPE must not break parsing.
        assertTrue(fountain.contains("Smith—alone."));
    }

    @Test
    void extractsPlainTextForSongsAndDrafts() throws Exception {
        byte[] epub = EpubExportServiceImpl.toEpub(new Project(), List.of(
                block(Block.TYPE_ACTION, "Jane enters.", null),
                block(Block.TYPE_LYRICS, "La la la", null)), 1);

        String plain = EpubToFountainConverter.convertPlain(new ByteArrayInputStream(epub));

        assertEquals("Jane enters.\nLa la la", plain);
    }

    @Test
    void rejectsFilesThatAreNotEpubs() {
        byte[] notAnEpub = "just some text".getBytes(StandardCharsets.UTF_8);

        assertThrows(IOException.class,
                () -> EpubToFountainConverter.convert(new ByteArrayInputStream(notAnEpub)));
    }

    @Test
    void recognizesEpubsByNameAndContentType() {
        assertTrue(EpubToFountainConverter.looksLikeEpub("script.epub", ""));
        assertTrue(EpubToFountainConverter.looksLikeEpub("script", "application/epub+zip"));
        assertTrue(!EpubToFountainConverter.looksLikeEpub("script.pdf", "application/pdf"));
    }

    private static byte[] thirdPartyEpub() throws IOException {
        String chapter = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <body>
                    <h1>Chapter One</h1>
                    <p>It was a dark and stormy night.</p>
                    <p>Smith&mdash;alone.</p>
                  </body>
                </html>
                """;
        String opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>A Novel</dc:title>
                  </metadata>
                  <manifest>
                    <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                  </spine>
                </package>
                """;
        String container = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """;
        return zip(Map.of(
                "mimetype", "application/epub+zip",
                "META-INF/container.xml", container,
                "EPUB/package.opf", opf,
                "EPUB/text/c1.xhtml", chapter));
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
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

    private static Project project() {
        Project project = new Project();
        project.setTitle("Demo");
        project.setScreenplayTitle("My Script");
        project.setWriters("written by\nJane Doe");
        project.setContactInfo("jane@example.com");
        project.setScreenplayVersion("Draft 1");
        return project;
    }

    private static Block block(String type, String content, Person person) {
        Block block = new Block();
        block.setType(type);
        block.setContent(content);
        block.setPerson(person);
        return block;
    }
}
