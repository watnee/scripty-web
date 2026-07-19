package com.scripty.api;

public final class ApiRel {

    public static final String USERS = "users";
    public static final String PROJECTS = "projects";
    public static final String BLOCKS = "blocks";
    public static final String CHARACTERS = "characters";
    public static final String ACTORS = "actors";
    public static final String TEAMS = "teams";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";
    public static final String PROJECT = "project";
    public static final String ACTOR = "actor";
    public static final String UNDO = "undo";
    public static final String REDO = "redo";
    public static final String UNDO_REDO_STATUS = "undoRedoStatus";
    public static final String SYNC_STATUS = "syncStatus";
    public static final String TOGGLE_BOOKMARK = "toggleBookmark";
    public static final String TOGGLE_PINNED = "togglePinned";
    public static final String CREATE_BELOW = "createBelow";
    public static final String CREATE_INITIAL = "createInitial";
    public static final String SET_TYPE = "setType";
    public static final String MOVE = "move";

    // Bulk operations act on a set of blocks at once, so they are advertised on
    // the block collection rather than on any one block.
    public static final String BULK_SET_TYPE = "bulkSetType";
    public static final String BULK_ADD_TAGS = "bulkAddTags";
    public static final String BULK_FORMAT = "bulkFormat";
    public static final String BULK_DELETE = "bulkDelete";
    public static final String BULK_REPLACE = "bulkReplace";

    // Recovery. Each collection that can lose things advertises its own trash.
    public static final String TRASH = "trash";
    public static final String PURGE = "purge";
    public static final String EMPTY_TRASH = "emptyTrash";

    // Collaboration.
    public static final String COMMENTS = "comments";
    public static final String ADD_COMMENT = "addComment";
    public static final String COMMENT_COUNTS = "commentCounts";
    public static final String ACTIVITY = "activity";
    public static final String ASSIGN_PRODUCTIONS = "assignProductions";
    public static final String INVITATIONS = "invitations";
    public static final String SEND_INVITATION = "sendInvitation";
    public static final String REVOKE = "revoke";

    // Named variants of a script. `editionId` was already accepted as a query
    // parameter; these are what let a client discover the ids to pass.
    public static final String EDITIONS = "editions";
    public static final String EDITION = "edition";
    public static final String SET_DEFAULT = "setDefault";
    public static final String SET_PUBLISHED = "setPublished";
    public static final String EXPORT = "export";
    public static final String EXPORT_PDF = "exportPdf";
    public static final String EXPORT_DOCX = "exportDocx";
    public static final String EXPORT_FDX = "exportFdx";
    public static final String HEADSHOT = "headshot";
    public static final String DOCUMENTS = "documents";
    public static final String DOCUMENT = "document";
    public static final String SONGS = "songs";
    public static final String SONG = "song";
    public static final String SONG_BLOCKS = "songBlocks";
    public static final String SET_HIGHLIGHT = "setHighlight";
    public static final String NOTES = "notes";
    public static final String INSERT = "insert";
    public static final String SHARE_EMAIL = "shareEmail";
    public static final String IMPORT_DOCUMENT = "importDocument";
    public static final String REORDER = "reorder";
    public static final String DUPLICATE = "duplicate";
    public static final String CHANGE_TYPE = "changeType";
    public static final String TOGGLE_DEFAULT = "toggleDefault";
    public static final String IMPORT_PROJECT = "importProject";
    public static final String VERSIONS = "versions";
    public static final String VERSION = "version";
    public static final String RESTORE = "restore";
    public static final String CREATE = "create";
    public static final String PREFERENCES = "preferences";
    public static final String CAPITALIZATION_PREFERENCES = "capitalizationPreferences";
    public static final String CONTACT_SUGGESTIONS = "contactSuggestions";
    public static final String IMPORT_SCRIPT = "importScript";

    private ApiRel() {
    }
}
