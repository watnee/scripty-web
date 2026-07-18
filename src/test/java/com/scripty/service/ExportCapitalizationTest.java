package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/** The per-type auto-capitalization preference has to survive into exported files. */
class ExportCapitalizationTest {

    private static final CapitalizationPreferences NONE =
            new CapitalizationPreferences(false, false, false, false);

    @Test
    void fdxKeepsTypedCaseWhenDisabled() {
        String xml = FdxExportServiceImpl.toFdx(project(), blocks(), NONE);

        assertTrue(xml.contains("int. kitchen - day"));
        assertTrue(xml.contains("cut to:"));
        assertTrue(xml.contains("close on the door"));
        assertFalse(xml.contains("INT. KITCHEN - DAY"));
        assertFalse(xml.contains("CUT TO:"));
    }

    @Test
    void fdxUppercasesByDefault() {
        String xml = FdxExportServiceImpl.toFdx(project(), blocks(), CapitalizationPreferences.ALL);

        assertTrue(xml.contains("INT. KITCHEN - DAY"));
        assertTrue(xml.contains("CUT TO:"));
        assertTrue(xml.contains("CLOSE ON THE DOOR"));
    }

    @Test
    void fdxRespectsASingleDisabledType() {
        // Only transitions opted out; the rest still uppercase.
        String xml = FdxExportServiceImpl.toFdx(project(), blocks(),
                new CapitalizationPreferences(true, true, false, true));

        assertTrue(xml.contains("INT. KITCHEN - DAY"));
        assertTrue(xml.contains("cut to:"));
        assertFalse(xml.contains("CUT TO:"));
    }

    @Test
    void epubDropsTextTransformForDisabledTypesOnly() throws Exception {
        byte[] epub = EpubExportServiceImpl.toEpub(project(), blocks(), 1,
                new CapitalizationPreferences(false, true, true, true));

        String css = stylesheet(epub);

        // Scene loses its transform; character keeps it.
        assertTrue(css.contains(".scene { font-weight: bold; margin-top: 1.5em; }"), css);
        assertTrue(css.contains(".character { margin: 0 0 0 20%; text-transform: uppercase; }"), css);
    }

    /** Returns the first .css entry in the EPUB zip. */
    private static String stylesheet(byte[] epub) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(epub))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith(".css")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }

    private static Project project() {
        Project project = new Project();
        project.setTitle("Demo");
        return project;
    }

    private static List<Block> blocks() {
        Person jane = new Person();
        jane.setName("Jane");
        return List.of(
                block(Block.TYPE_SCENE, "int. kitchen - day", null),
                block(Block.TYPE_ACTION, "Jane enters.", null),
                block(Block.TYPE_CHARACTER, "Jane", jane),
                block(Block.TYPE_DIALOGUE, "Hello there.", jane),
                block(Block.TYPE_TRANSITION, "cut to:", null),
                block(Block.TYPE_SHOT, "close on the door", null));
    }

    private static Block block(String type, String content, Person person) {
        Block block = new Block();
        block.setType(type);
        block.setContent(content);
        block.setPerson(person);
        return block;
    }
}
