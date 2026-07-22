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
    // Embed keys for the things a trash holds. `trash` is the link to it; a
    // collection needs its own name for the items inside.
    public static final String DELETED_BLOCK = "deletedBlock";
    public static final String DELETED_BLOCKS = "deletedBlocks";
    public static final String TRASHED_PROJECT = "trashedProject";
    public static final String TRASHED_PROJECTS = "trashedProjects";
    public static final String DELETED_DOCUMENT = "deletedDocument";
    public static final String DELETED_DOCUMENTS = "deletedDocuments";
    // A song's trashed lines are their own collection: a lyric line and a
    // screenplay element are restored by different services and previewed
    // differently, so mixing them under one embed key would hand a client two
    // shapes behind one name.
    public static final String DELETED_SONG_BLOCK = "deletedSongBlock";
    public static final String DELETED_SONG_BLOCKS = "deletedSongBlocks";
    public static final String PURGE = "purge";
    public static final String EMPTY_TRASH = "emptyTrash";

    // Collaboration.
    public static final String COMMENT = "comment";
    public static final String COMMENTS = "comments";
    public static final String ADD_COMMENT = "addComment";
    public static final String COMMENT_COUNTS = "commentCounts";
    public static final String ACTIVITY_ENTRY = "activityEntry";
    public static final String ACTIVITY = "activity";
    public static final String ASSIGN_PRODUCTIONS = "assignProductions";
    public static final String INVITATION = "invitation";
    public static final String INVITATIONS = "invitations";
    public static final String SEND_INVITATION = "sendInvitation";
    public static final String REVOKE = "revoke";
    // Who can already see a project, as opposed to who has been invited to.
    // Team membership and role grant access without any invitation, so the
    // invitation list alone never answers "who is reading this".
    public static final String ACCESS = "access";
    public static final String ACCESS_USER = "accessUser";

    // Named variants of a script. `editionId` was already accepted as a query
    // parameter; these are what let a client discover the ids to pass.
    public static final String EDITIONS = "editions";
    public static final String EDITION = "edition";
    public static final String SONG_EDITION = "songEdition";
    public static final String SONG_EDITIONS = "songEditions";
    public static final String SET_DEFAULT = "setDefault";
    public static final String SET_PUBLISHED = "setPublished";
    public static final String EXPORT = "export";
    public static final String EXPORT_PDF = "exportPdf";
    public static final String EXPORT_DOCX = "exportDocx";
    public static final String EXPORT_FDX = "exportFdx";
    public static final String EXPORT_EPUB = "exportEpub";
    /** The whole project as a re-importable .scripty.json archive. */
    public static final String EXPORT_ARCHIVE = "exportArchive";
    /**
     * Every project the caller can see, as one re-importable bundle. Advertised
     * on the project collection rather than on a project, because it is the
     * collection it exports; `ids` narrows it to a selection.
     */
    public static final String EXPORT_PROJECTS = "exportProjects";
    // A song exports on its own, in the formats SongExportService offers.
    public static final String EXPORT_SONG_TXT = "exportSongTxt";
    public static final String EXPORT_SONG_PDF = "exportSongPdf";
    public static final String EXPORT_SONG_DOCX = "exportSongDocx";
    public static final String EXPORT_SONG_EPUB = "exportSongEpub";
    /**
     * The lyric as a score, for setting to music in a notation program. The odd
     * one out among the song exports: the others are documents to read, this one
     * is meant to be opened and worked on, and it is the format
     * {@code importDocument} reads back.
     */
    public static final String EXPORT_SONG_MUSICXML = "exportSongMusicXml";
    // A project's songs gathered into one songbook, in the same formats. These
    // live on the document collection, since that is what they export; `ids`
    // narrows the songbook to a selection.
    public static final String EXPORT_SONGS_TXT = "exportSongsTxt";
    public static final String EXPORT_SONGS_PDF = "exportSongsPdf";
    public static final String EXPORT_SONGS_DOCX = "exportSongsDocx";
    public static final String EXPORT_SONGS_EPUB = "exportSongsEpub";
    /** Every song as sections of one score; MusicXML has no second piece. */
    public static final String EXPORT_SONGS_MUSICXML = "exportSongsMusicXml";
    // Which characters an actor auditions for, within a project. The ids ride on
    // the project-scoped actor resource; `setAuditions` is the action that
    // replaces the set. Per-project, so it is advertised only on a project-scoped
    // actor.
    public static final String SET_AUDITIONS = "setAuditions";
    public static final String HEADSHOT = "headshot";
    // Writing the headshot, as opposed to reading it. `setHeadshot` takes a
    // multipart image and is offered on every actor a caller may edit;
    // `removeHeadshot` is offered only where there is one to remove, so a client
    // needs no separate flag to decide whether to draw the control.
    public static final String SET_HEADSHOT = "setHeadshot";
    public static final String REMOVE_HEADSHOT = "removeHeadshot";

    // Password recovery, the one flow whose caller is signed out by definition.
    // Nothing behind the sign-in can advertise it, so `forgotPassword` rides on
    // the 401 challenge itself; `resetPassword` rides on the answer to a
    // request, and on a token that is still good — a token that has expired
    // simply arrives without it.
    public static final String FORGOT_PASSWORD = "forgotPassword";
    public static final String RESET_PASSWORD = "resetPassword";
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
    // A song embeds its snapshots under their own collection relation, so the
    // two histories stay apart where both could be in hand. The item relation
    // is VERSION either way — one saved version reads the same.
    public static final String SONG_VERSIONS = "songVersions";
    public static final String VERSION = "version";
    public static final String RESTORE = "restore";
    public static final String CREATE = "create";
    // The signed-in user's own account — not an admin's view of someone else's.
    // Advertised on the API root to anyone signed in; `passkeys` appears only
    // where passkeys are configured, and registering a new one stays a browser
    // ceremony, so the API offers listing and revoking only.
    public static final String ACCOUNT = "account";
    public static final String CHANGE_PASSWORD = "changePassword";
    public static final String PASSKEY = "passkey";
    public static final String PASSKEYS = "passkeys";
    public static final String CAPITALIZATION_PREFERENCES = "capitalizationPreferences";
    public static final String CONTACT_SUGGESTIONS = "contactSuggestions";
    public static final String IMPORT_SCRIPT = "importScript";

    private ApiRel() {
    }
}
