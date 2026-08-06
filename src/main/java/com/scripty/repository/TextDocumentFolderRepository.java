package com.scripty.repository;

import com.scripty.dto.TextDocumentFolder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Folders are not soft deleted. Nothing is lost when one goes — its documents
 * are unfiled, not trashed — so there is no deleted_at here and no trash to
 * list, unlike {@link TextDocumentRepository}.
 *
 * <p>Every lookup is scoped by project, and the listing ones by list as well:
 * a folder belongs to Songs or to Notes, and neither list is ever handed the
 * other's folders.
 */
public interface TextDocumentFolderRepository extends JpaRepository<TextDocumentFolder, Integer> {

    /**
     * One list's folders, in the order they are shown.
     *
     * <p>Alphabetical rather than an arrangement of the writer's own, unlike
     * the documents inside them. A folder has no order to preserve — it never
     * had one to scramble — and a list of names sorted by name is the least
     * surprising thing a heading can do when the name is all it shows.
     */
    List<TextDocumentFolder> findByProjectIdAndDocumentTypeOrderByNameAsc(
            Integer projectId, String documentType);

    /** Every folder of a project, both lists — what a whole-project read takes. */
    List<TextDocumentFolder> findByProjectIdOrderByDocumentTypeAscNameAsc(Integer projectId);

    Optional<TextDocumentFolder> findByIdAndProjectId(Integer id, Integer projectId);

    /** The name check behind "a folder called that is already here". */
    Optional<TextDocumentFolder> findByProjectIdAndDocumentTypeAndNameIgnoreCase(
            Integer projectId, String documentType, String name);
}
