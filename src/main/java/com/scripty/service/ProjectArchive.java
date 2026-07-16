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
        public String title;
        public String documentType;
        public String content;
        public Integer sortOrder;
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
