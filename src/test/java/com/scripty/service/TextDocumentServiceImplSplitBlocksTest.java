package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.TextDocument;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The notes editor writes list and heading prefixes as plain text. They are an
 * editing affordance, so inserting a note into a script must not print "- " or
 * "# " on the page.
 */
class TextDocumentServiceImplSplitBlocksTest {

    private TextDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TextDocumentServiceImpl(
                mock(TextDocumentRepository.class),
                mock(ProjectRepository.class),
                mock(BlockRepository.class),
                mock(BlockService.class),
                mock(ProjectService.class),
                mock(ScriptImportTextExtractor.class),
                mock(ProjectActivityService.class),
                mock(ScriptEditionService.class),
                mock(EmailService.class));
    }

    private List<String> split(String documentType, String content) {
        TextDocument doc = new TextDocument();
        doc.setId(1);
        doc.setDocumentType(documentType);
        doc.setContent(content);
        return service.splitContentIntoBlocks(doc, Block.TYPE_ACTION).stream()
                .map(CreateBlockBelowCommandModel::getContent)
                .collect(Collectors.toList());
    }

    @Test
    void stripsBulletAndHeadingPrefixesFromNotes() {
        List<String> blocks = split(TextDocument.TYPE_NOTES,
                "# Act One\n- alpha\n* beta\n1. first\n2. second");

        assertEquals(List.of("Act One", "alpha", "beta", "first", "second"), blocks);
    }

    @Test
    void stripsIndentOfNestedListItems() {
        List<String> blocks = split(TextDocument.TYPE_NOTES, "- outer\n    - nested");

        assertEquals(List.of("outer", "nested"), blocks);
    }

    @Test
    void leavesOrdinaryLinesUntouched() {
        // Leading whitespace only disappears when it precedes a real marker.
        List<String> blocks = split(TextDocument.TYPE_NOTES, "Sam pours coffee.\n    indented line");

        assertEquals(List.of("Sam pours coffee.", "    indented line"), blocks);
    }

    @Test
    void leavesHyphensThatAreNotListMarkers() {
        // No space after the hyphen, so it is punctuation rather than a bullet.
        List<String> blocks = split(TextDocument.TYPE_NOTES, "-dash\n3.14 is pi");

        assertEquals(List.of("-dash", "3.14 is pi"), blocks);
    }

    @Test
    void keepsSongLinesAsTyped() {
        // Songs never get the prefix affordances, so a leading dash is the
        // author's own and must survive.
        List<String> blocks = split(TextDocument.TYPE_SONG, "- ooh la la\n# 1 with a bullet");

        assertEquals(List.of("- ooh la la", "# 1 with a bullet"), blocks);
    }
}
