package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class FdxExportServiceImplTest {

    @Test
    void exportsTitlePageAndBodyElements() throws Exception {
        Project project = new Project();
        project.setTitle("Demo");
        project.setScreenplayTitle("My Script");
        project.setWriters("written by\nJane Doe");
        project.setContactInfo("jane@example.com");
        project.setScreenplayVersion("Draft 1");

        Person jane = new Person();
        jane.setName("Jane");

        String xml = FdxExportServiceImpl.toFdx(project, List.of(
                block(Block.TYPE_SCENE, "INT. KITCHEN - DAY", null),
                block(Block.TYPE_ACTION, "Jane enters.", null),
                block(Block.TYPE_CHARACTER, "JANE", jane),
                block(Block.TYPE_PARENTHETICAL, "quietly", null),
                block(Block.TYPE_DIALOGUE, "Hello there.", jane),
                block(Block.TYPE_TRANSITION, "CUT TO:", null),
                block(Block.TYPE_SHOT, "CLOSE ON THE DOOR", null)));

        assertTrue(xml.contains("<FinalDraft DocumentType=\"Script\""));
        assertTrue(xml.contains("<TitlePage>"));
        assertTrue(xml.contains("MY SCRIPT"));
        assertTrue(xml.contains("written by"));
        assertTrue(xml.contains("Jane Doe"));
        assertTrue(xml.contains("jane@example.com"));
        assertTrue(xml.contains("Draft 1"));
        assertTrue(xml.contains("Type=\"Scene Heading\""));
        assertTrue(xml.contains("INT. KITCHEN - DAY"));
        assertTrue(xml.contains("Type=\"Character\""));
        assertTrue(xml.contains(">JANE</Text>"));
        assertTrue(xml.contains("Type=\"Parenthetical\""));
        assertTrue(xml.contains("(quietly)"));
        assertTrue(xml.contains("Type=\"Dialogue\""));
        assertTrue(xml.contains("Hello there."));
        assertTrue(xml.contains("Type=\"Transition\""));
        assertTrue(xml.contains("CUT TO:"));
        assertTrue(xml.contains("Type=\"Shot\""));

        String fountain = FdxToFountainConverter.convert(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertTrue(fountain.contains("Title: MY SCRIPT"));
        assertTrue(fountain.contains("INT. KITCHEN - DAY"));
        assertTrue(fountain.contains("@JANE"));
    }

    @Test
    void exportsDualDialogue() {
        Person alice = new Person();
        alice.setName("Alice");
        Person bob = new Person();
        bob.setName("Bob");

        String xml = FdxExportServiceImpl.toFdx(new Project(), List.of(
                block(Block.TYPE_CHARACTER, "ALICE", alice),
                block(Block.TYPE_DIALOGUE, "Left side.", alice),
                block(Block.TYPE_DUAL_DIALOGUE, "BOB", bob),
                block(Block.TYPE_DIALOGUE, "Right side.", bob)));

        assertTrue(xml.contains("<DualDialogue>"));
        assertEquals(2, xml.split("<DualDialogue>", -1).length - 1);
        assertTrue(xml.contains("ALICE"));
        assertTrue(xml.contains("BOB"));
        assertTrue(xml.contains("Left side."));
        assertTrue(xml.contains("Right side."));
    }

    @Test
    void exportsPageBreakAsStartsNewPage() {
        String xml = FdxExportServiceImpl.toFdx(new Project(), List.of(
                block(Block.TYPE_ACTION, "Before break.", null),
                block(Block.TYPE_PAGE_BREAK, "", null),
                block(Block.TYPE_ACTION, "After break.", null)));

        assertTrue(xml.contains("StartsNewPage=\"Yes\""));
        assertTrue(xml.contains("After break."));
    }

    @Test
    void escapesXmlSpecialCharacters() {
        String xml = FdxExportServiceImpl.toFdx(new Project(), List.of(
                block(Block.TYPE_ACTION, "Tom & Jerry <say> \"hi\"", null)));

        assertTrue(xml.contains("Tom &amp; Jerry &lt;say&gt; &quot;hi&quot;"));
    }

    private static Block block(String type, String content, Person person) {
        Block block = new Block();
        block.setType(type);
        block.setContent(content);
        block.setPerson(person);
        return block;
    }
}
