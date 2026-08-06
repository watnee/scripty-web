package com.scripty.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON schema for a full-project export file (.scripty.json). Keys are the
 * exporting database ids and only exist to wire up references inside the file;
 * import assigns fresh ids.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectArchive {

    public static final String FORMAT = "scripty-project";
    public static final int CURRENT_VERSION = 1;

    // Deliberately not defaulted: a parsed file must carry its own format
    // marker and version for import validation to mean anything.
    public String format;
    public int formatVersion;
    public String exportedAt;
    public Info project;
    public List<Edition> editions = new ArrayList<>();
    public List<Character> characters = new ArrayList<>();
    public List<Document> documents = new ArrayList<>();
    public List<BlockEntry> blocks = new ArrayList<>();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Info {
        public String title;
        public String screenplayTitle;
        public String writers;
        public String contactInfo;
        public String screenplayVersion;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Edition {
        public Integer key;
        public String name;
        public boolean defaultEdition;
        public boolean published;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Character {
        public Integer key;
        public String name;
        public String fullName;
        public Integer editionKey;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Document {
        public Integer key;
        /**
         * What this song or note is, across every place it is kept — see
         * {@link com.scripty.dto.TextDocument#getUid()}.
         *
         * Unlike {@code key}, which only wires up references inside this file
         * and is thrown away on import, this travels: a file read back into the
         * project it came from matches its documents on this and updates them
         * where they stand, so a song keeps its id, its lyric lines and its
         * version history instead of being replaced by a copy of itself.
         *
         * Absent in files written before it existed. Those simply match nothing,
         * which is exactly the behaviour they had.
         */
        public String uid;
        public String title;
        public String documentType;
        public String content;
        public Integer sortOrder;
        /**
         * Whether this song or note was put aside rather than listed. Added
         * after the format was first written, so a file without it simply reads
         * as "not archived" — which is what every older export meant.
         */
        public boolean archived;
        /**
         * The folder this song or note was filed under, by name.
         *
         * A name rather than a key, because that is the whole of what a folder
         * is — and because it is what lets an arrangement survive a crossing
         * between two workspaces that number their folders separately, the same
         * problem {@link #uid} solves for the documents themselves. On the way
         * in, a folder of that name in this document's list is used, and one is
         * made where there is none.
         *
         * Absent in files written before folders existed, and absent for an
         * unfiled document, which reads the same way: not in a folder.
         */
        public String folder;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BlockEntry {
        public Integer order;
        public String type;
        public String content;
        public boolean sceneDelimiter;
        public String textAlign;
        public String font;
        public String highlight;
        public boolean textBold;
        public boolean textItalic;
        public boolean textUnderline;
        public boolean bookmarked;
        public boolean pinned;
        public String tags;
        public Integer editionKey;
        public Integer characterKey;
        public Integer sourceDocumentKey;
    }
}
