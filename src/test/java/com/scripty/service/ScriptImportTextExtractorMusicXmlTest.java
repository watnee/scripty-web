package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * The import side of MusicXML: how a score reaches a song. The words and line
 * breaks themselves are covered by {@link MusicXmlToLyricsConverterTest}; what
 * matters here is that a score is recognized as one rather than imported as its
 * own markup.
 */
class ScriptImportTextExtractorMusicXmlTest {

    private final ScriptImportTextExtractor extractor = new ScriptImportTextExtractor();

    private static byte[] score() {
        return SongMusicXmlWriter.write("Hold The Line",
                List.of(new SongMusicXmlWriter.Song(null, "Hold the line\nWait for morning")));
    }

    @Test
    void readsTheWordsOutOfAScore() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "hold-the-line.musicxml",
                "application/vnd.recordare.musicxml+xml", score());

        assertEquals("Hold the line\nWait for morning", extractor.extractPlain(file));
        assertEquals("Hold The Line", extractor.extractTitle(file));
    }

    @Test
    void recognizesAScoreThatOnlyCallsItselfXml() throws IOException {
        // Notation programs have written plain .xml for years; taken at its
        // extension the file would import as its own markup.
        MultipartFile file = new MockMultipartFile("file", "score.xml", "text/xml", score());

        assertEquals("Hold the line\nWait for morning", extractor.extractPlain(file));
    }

    @Test
    void leavesOrdinaryXmlAlone() throws IOException {
        byte[] notAScore = "<notes><note>buy milk</note></notes>".getBytes(StandardCharsets.UTF_8);
        MultipartFile file = new MockMultipartFile("file", "notes.xml", "text/xml", notAScore);

        assertTrue(extractor.extractPlain(file).contains("<note>"));
        assertNull(extractor.extractTitle(file));
    }

    @Test
    void onlyAScoreDeclaresATitleOfItsOwn() throws IOException {
        MultipartFile text = new MockMultipartFile("file", "lyrics.txt", "text/plain",
                "Hold the line".getBytes(StandardCharsets.UTF_8));

        assertNull(extractor.extractTitle(text));
    }
}
