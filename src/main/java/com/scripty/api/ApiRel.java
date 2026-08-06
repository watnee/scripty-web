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
    // Replace one occurrence in one block — the single-step "Replace" beside the
    // block collection's "Replace All" (BULK_REPLACE). Per block, so it rides
    // here rather than on the collection.
    public static final String REPLACE = "replace";

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
    // The teams a collaborator can be invited into. Inviting an editor needs a
    // team, and a project's teams are the only valid choices, so a client that
    // cannot read this list cannot send a valid invitation at all.
    public static final String INVITE_TEAMS = "inviteTeams";
    public static final String INVITE_TEAM = "inviteTeam";
    // The project side of team membership: every team the writer could assign
    // this project to, each flagged assigned-or-not, so the production page's
    // team checkboxes can be built and saved through the project's `update`
    // affordance. Its own list because `inviteTeams` deliberately shows only the
    // project's current teams, not the ones it could gain.
    public static final String PROJECT_TEAMS = "projectTeams";
    public static final String PROJECT_TEAM = "projectTeam";
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
     * The other direction of {@link #EXPORT_ARCHIVE}: an archive read back into
     * the project that is already here, rather than into a new one.
     *
     * <p>This is what lets a copy kept somewhere else stay the same screenplay.
     * A device that wrote without an account keeps its own copy after handing
     * one to the account, goes on writing in it while signed out, and needs
     * somewhere to put those words that is not a second screenplay —
     * {@link #IMPORT_PROJECT} can only ever make one of those.
     *
     * <p>Advertised to editors only, and never a way to lose work: the script
     * as it stands is saved to the version history before the incoming one
     * replaces it.
     */
    public static final String REPLACE_FROM_ARCHIVE = "replaceFromArchive";
    /**
     * Every project the caller can see, as one re-importable bundle. Advertised
     * on the project collection rather than on a project, because it is the
     * collection it exports; `ids` narrows it to a selection.
     */
    public static final String EXPORT_PROJECTS = "exportProjects";
    // A document exports on its own, in the formats SongExportService offers.
    // Named for songs because songs had them first; a note now carries the same
    // four, since the renderer only ever laid out a title and its lines and a
    // note has both. MusicXML below is the one that stays song-only.
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
    // The same gathering made of notes. Rels of their own rather than a shared
    // set, because the two lists are exported separately and a client showing
    // one of them has to be able to tell which href belongs to it — the href
    // differs only by its `type`, which a rel name is exactly the right place
    // to record. No MusicXML: a page of scene notes is not a thing to set to
    // music, and the endpoint refuses it.
    public static final String EXPORT_NOTES_TXT = "exportNotesTxt";
    public static final String EXPORT_NOTES_PDF = "exportNotesPdf";
    public static final String EXPORT_NOTES_DOCX = "exportNotesDocx";
    public static final String EXPORT_NOTES_EPUB = "exportNotesEpub";
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
    // Folders, the headings a writer files a list's songs or notes under.
    //
    // FOLDERS is the collection, advertised on the document collection and
    // scoped by `type`, because a folder belongs to Songs or to Notes and
    // neither list is ever handed the other's. CREATE_FOLDER rides on that
    // collection; RENAME and DELETE ride on a folder, so a client draws the two
    // controls only where the server would take them.
    //
    // MOVE_TO_FOLDER is on the *document*, not on the folder: it is a write to
    // the document (which folder it is in), the folder itself holds nothing.
    // BULK_MOVE_TO_FOLDER is the selection form, on the document collection
    // beside the other bulk rels. Both take a folder id, and both read a
    // missing one as "take it out of its folder" — there is no separate unfile
    // rel, since a document out of every folder is the ordinary state.
    public static final String FOLDER = "folder";
    public static final String FOLDERS = "folders";
    public static final String CREATE_FOLDER = "createFolder";
    public static final String RENAME_FOLDER = "renameFolder";
    public static final String DELETE_FOLDER = "deleteFolder";
    public static final String MOVE_TO_FOLDER = "moveToFolder";
    public static final String BULK_MOVE_TO_FOLDER = "bulkMoveToFolder";
    public static final String SONGS = "songs";
    public static final String SONG = "song";
    public static final String SONG_BLOCKS = "songBlocks";
    public static final String SET_HIGHLIGHT = "setHighlight";
    public static final String NOTES = "notes";
    public static final String INSERT = "insert";
    public static final String SHARE_EMAIL = "shareEmail";
    public static final String BULK_SHARE_EMAIL = "bulkShareEmail";
    public static final String IMPORT_DOCUMENT = "importDocument";
    public static final String REORDER = "reorder";
    public static final String DUPLICATE = "duplicate";
    public static final String CHANGE_TYPE = "changeType";
    // Archiving. Separate from the trash: nothing here expires, and an archived
    // document is still whole — ARCHIVED is the collection it went to, ARCHIVE
    // and UNARCHIVE the two directions, BULK_ARCHIVE and BULK_UNARCHIVE the
    // selection forms of each. The two selection rels do not sit on the same
    // resource: one rides the list, the other the archive, because that is
    // where each one's selection is made.
    public static final String ARCHIVE = "archive";
    public static final String UNARCHIVE = "unarchive";
    public static final String ARCHIVED = "archived";
    public static final String BULK_ARCHIVE = "bulkArchive";
    public static final String BULK_UNARCHIVE = "bulkUnarchive";
    public static final String ARCHIVED_DOCUMENT = "archivedDocument";
    public static final String ARCHIVED_DOCUMENTS = "archivedDocuments";
    // The same three verbs one level up, for a whole screenplay. ARCHIVE and
    // UNARCHIVE are reused as-is — they read the same on either resource, and a
    // client follows the link it was handed rather than matching on the noun.
    // Only the embed keys have to differ, since a project and a song are not
    // interchangeable in a list.
    public static final String ARCHIVED_PROJECT = "archivedProject";
    public static final String ARCHIVED_PROJECTS = "archivedProjects";
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
    // where passkeys are configured.
    public static final String ACCOUNT = "account";
    public static final String CHANGE_PASSWORD = "changePassword";
    public static final String PASSKEY = "passkey";
    public static final String PASSKEYS = "passkeys";
    // The WebAuthn ceremonies, opened up to native clients: each rel points at
    // an options endpoint, whose response carries a `verify` link for the
    // second half of the ceremony. REGISTER_PASSKEY rides on the passkeys
    // collection (you must be signed in to add one); PASSKEY_LOGIN rides on
    // the signed-out 401 challenge, the one document an anonymous caller sees.
    public static final String REGISTER_PASSKEY = "registerPasskey";
    public static final String PASSKEY_LOGIN = "passkeyLogin";
    public static final String VERIFY = "verify";
    // Where a passkey sign-in's bearer token can be revoked (the API's sign-out).
    public static final String REVOKE_TOKEN = "revokeToken";
    public static final String CAPITALIZATION_PREFERENCES = "capitalizationPreferences";
    public static final String CONTACT_SUGGESTIONS = "contactSuggestions";
    public static final String IMPORT_SCRIPT = "importScript";

    private ApiRel() {
    }
}
