package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.dto.TextDocument;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.EntityModel;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * What a note is offered on its own resource, now that it is offered the same
 * things a song is.
 *
 * <p>The client is a strict hypermedia client: an affordance it is not sent a
 * rel for simply does not appear. So the whole of "notes can be exported and
 * emailed too" is these links being here — which makes them worth pinning
 * rather than leaving to the assembler's shape.
 *
 * <p>Two things stay song-only, and both are pinned below for the same reason:
 * MusicXML, because a score of scene notes is not a thing, and the lyric-block
 * rels, because a note has no blocks to edit or version.
 */
class DocumentParityRelTest {

    private static final int PROJECT_ID = 7;

    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final TextDocumentResourceAssembler documents = new TextDocumentResourceAssembler();

    @BeforeEach
    void setUp() {
        documents.projectAccess = projectAccess;
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(true);
        // linkTo(methodOn(...)) needs a current request to build absolute hrefs.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private EntityModel<TextDocumentResource> model(String type) {
        TextDocumentViewModel document = new TextDocumentViewModel();
        document.setId(11);
        document.setProjectId(PROJECT_ID);
        document.setTitle("Scene 4 research");
        document.setDocumentType(type);
        return documents.toModel(document);
    }

    @Test
    void aNoteCarriesTheSameFourDocumentExportsASongDoes() {
        EntityModel<TextDocumentResource> note = model(TextDocument.TYPE_NOTES);

        assertTrue(note.getLink(ApiRel.EXPORT_SONG_TXT).isPresent());
        assertTrue(note.getLink(ApiRel.EXPORT_SONG_PDF).isPresent());
        assertTrue(note.getLink(ApiRel.EXPORT_SONG_DOCX).isPresent());
        assertTrue(note.getLink(ApiRel.EXPORT_SONG_EPUB).isPresent());
    }

    @Test
    void aNoteCanBeEmailedToACollaborator() {
        assertTrue(model(TextDocument.TYPE_NOTES).getLink(ApiRel.SHARE_EMAIL).isPresent());
    }

    @Test
    void onlyASongIsOfferedAScore() {
        assertTrue(model(TextDocument.TYPE_SONG).getLink(ApiRel.EXPORT_SONG_MUSICXML).isPresent());
        assertFalse(model(TextDocument.TYPE_NOTES).getLink(ApiRel.EXPORT_SONG_MUSICXML).isPresent());
    }

    /**
     * A note is plain content: there are no lyric lines to reorder and no
     * version history over them. This is the one place the two kinds are still
     * genuinely different, rather than differing because nobody had lifted a
     * gate.
     */
    @Test
    void onlyASongIsEditedAsBlocksAndVersioned() {
        EntityModel<TextDocumentResource> note = model(TextDocument.TYPE_NOTES);

        assertFalse(note.getLink(ApiRel.SONG_BLOCKS).isPresent());
        assertFalse(note.getLink(ApiRel.VERSIONS).isPresent());
        assertFalse(note.getLink(ApiRel.EDITIONS).isPresent());

        EntityModel<TextDocumentResource> song = model(TextDocument.TYPE_SONG);
        assertTrue(song.getLink(ApiRel.SONG_BLOCKS).isPresent());
    }

    /** Exporting is a read, so a view-only collaborator keeps it — for notes too. */
    @Test
    void aReaderCanStillTakeANoteAway() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(false);
        EntityModel<TextDocumentResource> note = model(TextDocument.TYPE_NOTES);

        assertTrue(note.getLink(ApiRel.EXPORT_SONG_TXT).isPresent());
        // Emailing is a write in the sense that matters: it sits behind the
        // edit gate, as it always has for songs.
        assertFalse(note.getLink(ApiRel.SHARE_EMAIL).isPresent());
    }
}
