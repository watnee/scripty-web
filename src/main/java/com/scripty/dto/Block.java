package com.scripty.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "`block`")
public class Block {

    // Fountain screenplay element types
    public static final String TYPE_SCENE = "SCENE";
    public static final String TYPE_ACTION = "ACTION";
    public static final String TYPE_DIALOGUE = "DIALOGUE";
    public static final String TYPE_PARENTHETICAL = "PARENTHETICAL";
    public static final String TYPE_TRANSITION = "TRANSITION";
    public static final String TYPE_LYRICS = "LYRICS";
    public static final String TYPE_CENTERED = "CENTERED";
    public static final String TYPE_SECTION = "SECTION";
    public static final String TYPE_SYNOPSIS = "SYNOPSIS";
    public static final String TYPE_NOTE = "NOTE";
    public static final String TYPE_PAGE_BREAK = "PAGE_BREAK";

    public static final java.util.Set<String> ELEMENT_TYPES = java.util.Set.of(
            TYPE_SCENE, TYPE_ACTION, TYPE_DIALOGUE, TYPE_PARENTHETICAL,
            TYPE_TRANSITION, TYPE_LYRICS, TYPE_CENTERED,
            TYPE_SECTION, TYPE_SYNOPSIS, TYPE_NOTE, TYPE_PAGE_BREAK);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "`order`", nullable = false)
    private Integer order;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean bookmarked;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "`type`", nullable = false)
    private String type = TYPE_ACTION;

    @Column(name = "scene_delimiter", nullable = false)
    private boolean sceneDelimiter = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Transient
    public boolean isScene() {
        return TYPE_SCENE.equals(type);
    }

    public boolean isSceneDelimiter() {
        return sceneDelimiter;
    }

    public void setSceneDelimiter(boolean sceneDelimiter) {
        this.sceneDelimiter = sceneDelimiter;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public boolean isBookmarked() {
        return bookmarked;
    }

    public void setBookmarked(boolean bookmarked) {
        this.bookmarked = bookmarked;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    @Column(name = "tags")
    private String tags;

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
