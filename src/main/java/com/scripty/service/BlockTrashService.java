package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.User;
import com.scripty.viewmodel.block.trash.DeletedBlockListViewModel;

/**
 * The block trash: lists a project's deleted blocks and puts them back. Capture
 * happens in {@link BlockService} at delete time; this is the recovery side.
 */
public interface BlockTrashService {

    /**
     * The project's trashed blocks, newest first, each with its purge date.
     * @return the trash view, or null if the project isn't accessible to the user
     */
    DeletedBlockListViewModel getTrashViewModel(Integer projectId, User currentUser);

    /**
     * Re-creates a trashed block in the script and drops its trash record.
     * @return the restored block, or null if it isn't in this project's trash
     *         (already restored, purged, or the user can't access the project)
     */
    Block restore(Integer deletedBlockId, Integer projectId, User currentUser);

    /**
     * Permanently removes one block from the trash. Only reachable from the trash,
     * so a delete is always recoverable first.
     * @return true if a record was removed
     */
    boolean purge(Integer deletedBlockId, Integer projectId, User currentUser);

    /**
     * Removes every trashed block past the retention window.
     * @return how many were purged
     */
    int purgeExpired();

    /** How many days a deleted block stays recoverable. */
    int getRetentionDays();
}
