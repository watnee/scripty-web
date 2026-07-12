package com.scripty.service;

import com.scripty.commandmodel.textdocument.TextDocumentCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.viewmodel.textdocument.TextDocumentListViewModel;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import java.io.IOException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface TextDocumentService {

    TextDocument read(Integer id);

    TextDocumentListViewModel getListViewModel(Integer projectId, User currentUser);

    TextDocumentViewModel getViewModel(Integer id, User currentUser);

    TextDocumentCommandModel getCommandModel(Integer id, User currentUser);

    TextDocumentCommandModel getNewCommandModel(Integer projectId);

    TextDocumentCommandModel getNewCommandModel(Integer projectId, String documentType);

    TextDocument save(TextDocumentCommandModel commandModel, User currentUser);

    /**
     * Renames a document without touching its content. Blank titles are ignored.
     * @return the renamed document, or null if not found/accessible or the title was blank
     */
    TextDocument rename(Integer id, Integer projectId, String title, User currentUser);

    void delete(Integer id, Integer projectId, User currentUser);

    /**
     * Inserts document content into the screenplay as typed blocks (default LYRICS for songs).
     * Created blocks are linked to the document so later song/draft edits can sync.
     * @param documentId document to insert
     * @param afterBlockId block to insert after; if null, appends after the last block
     * @param asType Fountain block type override; if null, uses LYRICS for songs and ACTION otherwise
     * @return created blocks
     */
    List<Block> insertIntoScript(Integer documentId, Integer afterBlockId, String asType, User currentUser);

    /**
     * Updates every script insertion of this document to match its current content.
     * @return true if any linked script blocks were changed
     */
    boolean syncInsertedBlocks(Integer documentId, User currentUser);

    /**
     * Import a text/Word file as a new song or draft document.
     * @param type SONG or NOTES (drafts)
     */
    TextDocument importFile(Integer projectId, String type, MultipartFile file, User currentUser) throws IOException;
}
