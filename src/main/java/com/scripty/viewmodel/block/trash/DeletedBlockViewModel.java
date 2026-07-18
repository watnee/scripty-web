package com.scripty.viewmodel.block.trash;

import java.time.LocalDateTime;

/** One recoverable block on the block trash page. */
public class DeletedBlockViewModel {

    private final int id;
    private final String preview;
    private final boolean empty;
    private final String typeLabel;
    private final String editionName;
    private final String deletedByName;
    private final LocalDateTime deletedAt;
    private final LocalDateTime purgeAt;

    public DeletedBlockViewModel(int id, String preview, boolean empty, String typeLabel,
                                 String editionName, String deletedByName,
                                 LocalDateTime deletedAt, LocalDateTime purgeAt) {
        this.id = id;
        this.preview = preview;
        this.empty = empty;
        this.typeLabel = typeLabel;
        this.editionName = editionName;
        this.deletedByName = deletedByName;
        this.deletedAt = deletedAt;
        this.purgeAt = purgeAt;
    }

    public int getId() {
        return id;
    }

    public String getPreview() {
        return preview;
    }

    /** True when the block had no text, so the page can say so instead of showing nothing. */
    public boolean isEmpty() {
        return empty;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    /** Which edition the block was deleted from; null when the script has only one. */
    public String getEditionName() {
        return editionName;
    }

    public String getDeletedByName() {
        return deletedByName;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /** When the purge job will remove this block for good. */
    public LocalDateTime getPurgeAt() {
        return purgeAt;
    }
}
