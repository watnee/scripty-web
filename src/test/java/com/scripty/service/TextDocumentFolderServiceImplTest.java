package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.TextDocument;
import com.scripty.dto.TextDocumentFolder;
import com.scripty.dto.User;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentFolderRepository;
import com.scripty.repository.TextDocumentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The promises a folder makes, in the order a writer would notice them being
 * broken: a name that is actually free, a list that keeps to itself, and a
 * removal that takes away only the name.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TextDocumentFolderServiceImplTest {

    @Mock
    private TextDocumentFolderRepository folderRepository;
    @Mock
    private TextDocumentRepository textDocumentRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectService projectService;
    @Mock
    private ProjectActivityService projectActivityService;

    @InjectMocks
    private TextDocumentFolderServiceImpl service;

    private Project project;
    private User user;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(7);
        user = new User();
        user.setId(1);
        when(projectRepository.findWithTeamsById(7)).thenReturn(Optional.of(project));
        when(projectService.canUserAccessProject(project, user)).thenReturn(true);
        when(folderRepository.save(any(TextDocumentFolder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAFolderInTheListItWasAskedFor() {
        when(folderRepository.findByProjectIdAndDocumentTypeAndNameIgnoreCase(7, "NOTES", "Research"))
                .thenReturn(Optional.empty());

        TextDocumentFolder folder = service.create(7, TextDocument.TYPE_NOTES, "  Research  ", user);

        assertNotNull(folder);
        assertEquals("Research", folder.getName(), "the name is trimmed");
        assertEquals(TextDocument.TYPE_NOTES, folder.getDocumentType());
        assertEquals(project, folder.getProject());
    }

    /**
     * Anything unrecognised is the notes list, matching the rule the list page
     * follows — notes are everything that is not a song.
     */
    @Test
    void anUnknownTypeIsTheNotesList() {
        TextDocumentFolder folder = service.create(7, "SOMETHING ELSE", "Odds and ends", user);

        assertNotNull(folder);
        assertEquals(TextDocument.TYPE_NOTES, folder.getDocumentType());
    }

    @Test
    void refusesABlankName() {
        assertThrows(DocumentFolderException.class, () -> service.create(7, "SONG", "   ", user));
        verify(folderRepository, never()).save(any());
    }

    @Test
    void refusesANameAlreadyUsedInThatList() {
        TextDocumentFolder existing = folder(4, "SONG", "Act One");
        when(folderRepository.findByProjectIdAndDocumentTypeAndNameIgnoreCase(7, "SONG", "act one"))
                .thenReturn(Optional.of(existing));

        DocumentFolderException thrown = assertThrows(DocumentFolderException.class,
                () -> service.create(7, "SONG", "act one", user));
        assertEquals("There is already a folder called “Act One” here.", thrown.getMessage());
    }

    /** A folder renamed to what it is already called is not a clash with itself. */
    @Test
    void renamingToTheSameNameIsNotAClash() {
        TextDocumentFolder existing = folder(4, "SONG", "Act One");
        when(folderRepository.findByIdAndProjectId(4, 7)).thenReturn(Optional.of(existing));

        assertEquals("Act One", service.rename(4, 7, "Act One", user).getName());
        verify(folderRepository, never()).findByProjectIdAndDocumentTypeAndNameIgnoreCase(
                anyInt(), anyString(), anyString());
    }

    /**
     * The one thing anyone hesitates over: removing a folder takes away the
     * name and nothing else.
     */
    @Test
    void removingAFolderUnfilesItsDocumentsInsteadOfDeletingThem() {
        TextDocumentFolder existing = folder(4, "SONG", "Act One");
        when(folderRepository.findByIdAndProjectId(4, 7)).thenReturn(Optional.of(existing));
        TextDocument one = document("SONG", existing);
        TextDocument two = document("SONG", existing);
        when(textDocumentRepository.findByFolder_Id(4)).thenReturn(List.of(one, two));

        assertEquals(2, service.delete(4, 7, user), "says how many it let go");

        assertNull(one.getFolder());
        assertNull(two.getFolder());
        verify(textDocumentRepository).save(one);
        verify(textDocumentRepository).save(two);
        verify(textDocumentRepository, never()).delete(any());
        verify(folderRepository).delete(existing);
    }

    @Test
    void removingAFolderThatIsNotHereSaysSoWithoutTouchingAnything() {
        when(folderRepository.findByIdAndProjectId(4, 7)).thenReturn(Optional.empty());

        assertEquals(-1, service.delete(4, 7, user), "told apart from an empty folder");
        verify(folderRepository, never()).delete(any());
    }

    /** A song is never filed under a notes folder. */
    @Test
    void refusesAFolderFromTheOtherList() {
        TextDocumentFolder notesFolder = folder(4, TextDocument.TYPE_NOTES, "Research");
        when(folderRepository.findByIdAndProjectId(4, 7)).thenReturn(Optional.of(notesFolder));
        TextDocument song = document(TextDocument.TYPE_SONG, null);
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(11, 7))
                .thenReturn(Optional.of(song));

        assertNull(service.moveDocument(11, 7, 4, user));
        assertNull(song.getFolder(), "and leaves it where it was");
    }

    @Test
    void filingAndUnfilingOneDocument() {
        TextDocumentFolder songFolder = folder(4, TextDocument.TYPE_SONG, "Act One");
        when(folderRepository.findByIdAndProjectId(4, 7)).thenReturn(Optional.of(songFolder));
        TextDocument song = document(TextDocument.TYPE_SONG, null);
        song.setProject(project);
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(11, 7))
                .thenReturn(Optional.of(song));

        assertNotNull(service.moveDocument(11, 7, 4, user));
        assertEquals(songFolder, song.getFolder());

        // A null folder id is the way out; there is no separate unfile call.
        assertNotNull(service.moveDocument(11, 7, null, user));
        assertNull(song.getFolder());
    }

    /**
     * A selection made before someone else changed a song into a note still
     * moves everything it can.
     */
    @Test
    void aSelectionSkipsWhatTheFolderCannotTake() {
        TextDocumentFolder songFolder = folder(4, TextDocument.TYPE_SONG, "Act One");
        when(folderRepository.findByIdAndProjectId(4, 7)).thenReturn(Optional.of(songFolder));
        TextDocument song = document(TextDocument.TYPE_SONG, null);
        TextDocument note = document(TextDocument.TYPE_NOTES, null);
        song.setProject(project);
        note.setProject(project);
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(11, 7))
                .thenReturn(Optional.of(song));
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(12, 7))
                .thenReturn(Optional.of(note));
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(99, 7))
                .thenReturn(Optional.empty());

        assertEquals(1, service.moveDocuments(List.of(11, 12, 99), 7, 4, user));
        assertEquals(songFolder, song.getFolder());
        assertNull(note.getFolder());
    }

    @Test
    void aProjectNobodyCanReachAnswersNothing() {
        when(projectService.canUserAccessProject(project, user)).thenReturn(false);

        assertNull(service.list(7, "SONG", user));
        assertNull(service.create(7, "SONG", "Act One", user));
        assertEquals(-1, service.delete(4, 7, user));
        assertEquals(0, service.moveDocuments(List.of(11), 7, 4, user));
    }

    private TextDocumentFolder folder(Integer id, String type, String name) {
        TextDocumentFolder folder = new TextDocumentFolder();
        folder.setId(id);
        folder.setProject(project);
        folder.setDocumentType(type);
        folder.setName(name);
        return folder;
    }

    private TextDocument document(String type, TextDocumentFolder folder) {
        TextDocument document = new TextDocument();
        document.setDocumentType(type);
        document.setTitle("A document");
        document.setFolder(folder);
        return document;
    }
}
