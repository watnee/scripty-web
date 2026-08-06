package com.scripty.service;

import com.scripty.dto.TextDocument;
import com.scripty.dto.TextDocumentFolder;
import com.scripty.dto.User;
import java.util.List;

/**
 * Folders for a project's songs and notes.
 *
 * <p>Kept apart from {@link TextDocumentService} because a folder is not a
 * document and shares none of its machinery — no trash, no archive, no
 * versions, no script insertions, nothing to export. The one place the two meet
 * is {@link #moveDocument}, which is a write to the document rather than to the
 * folder, and lives here because the rules it enforces are the folder's.
 *
 * <p>Every method returns null for the failures that are not worth explaining —
 * an unknown project, a folder from somewhere else, no permission — and throws
 * {@link DocumentFolderException} for the two that are: a blank name, and a
 * name already in use in this list.
 */
public interface TextDocumentFolderService {

    /**
     * One list's folders, alphabetically.
     * @param documentType SONG or NOTES; anything else is read as NOTES
     * @return the folders, or null if the project isn't accessible
     */
    List<TextDocumentFolder> list(Integer projectId, String documentType, User currentUser);

    /** Every folder of a project, both lists, for a page that shows both. */
    List<TextDocumentFolder> listAll(Integer projectId, User currentUser);

    /**
     * Makes a folder in one of a project's two lists.
     * @return the new folder, or null if the project isn't accessible
     * @throws DocumentFolderException if the name is blank or already taken
     */
    TextDocumentFolder create(Integer projectId, String documentType, String name, User currentUser);

    /**
     * Renames a folder. Its documents are untouched — a folder is only a name.
     * @return the folder, or null if it isn't in this project
     * @throws DocumentFolderException if the name is blank or already taken
     */
    TextDocumentFolder rename(Integer id, Integer projectId, String name, User currentUser);

    /**
     * Removes a folder and unfiles what was in it.
     *
     * <p>Never deletes a document. Everything the folder held stays in the
     * list, in the same order, simply without a heading over it.
     *
     * @return how many documents were unfiled, or -1 if the folder isn't in
     *         this project — which a caller tells apart from a folder that was
     *         empty (0) when it wants to say what happened
     */
    int delete(Integer id, Integer projectId, User currentUser);

    /**
     * Files a document under a folder, or unfiles it when {@code folderId} is
     * null.
     *
     * <p>The folder must belong to the same project and to the same list as the
     * document: a song is never filed under a notes folder. A document already
     * in that folder is left alone.
     *
     * @return the document, or null if either it or the folder isn't here
     */
    TextDocument moveDocument(Integer documentId, Integer projectId, Integer folderId, User currentUser);

    /**
     * Files several documents at once — what the list's tick-a-few-rows
     * selection sends. Ids that are missing, from another project, or of the
     * wrong list for this folder are skipped, so a selection that went stale is
     * harmless.
     *
     * @return how many documents actually moved
     */
    int moveDocuments(List<Integer> documentIds, Integer projectId, Integer folderId, User currentUser);
}
