/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.scene.sceneprofile;

/**
 *
 * @author chris
 */
public class BlockViewModel {
    
    private int id;
    private int order;
    private String content;
    private int personId;
    private String personName;
    private boolean bookmarked;
    private boolean pinned;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getPersonId() {
        return personId;
    }

    public void setPersonId(int personId) {
        this.personId = personId;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
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

    private String tags;

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    /**
     * Classifies the block as a standard screenplay element so templates can
     * apply the matching formatting: scene-heading, action, dialogue,
     * parenthetical or transition.
     */
    public String getElementType() {
        String text = content == null ? "" : content.trim();
        boolean hasPerson = personName != null && !personName.trim().isEmpty();
        boolean parenthetical = text.startsWith("(") && text.endsWith(")");
        if (hasPerson) {
            return parenthetical ? "parenthetical" : "dialogue";
        }
        String upper = text.toUpperCase();
        if (upper.startsWith("INT.") || upper.startsWith("EXT.")
                || upper.startsWith("INT ") || upper.startsWith("EXT ")
                || upper.startsWith("INT/EXT") || upper.startsWith("I/E ")
                || upper.startsWith("EST.")) {
            return "scene-heading";
        }
        boolean singleLine = !text.contains("\n");
        if (singleLine && (upper.endsWith("TO:")
                || upper.startsWith("FADE IN") || upper.startsWith("FADE OUT")
                || upper.startsWith("FADE TO") || upper.startsWith("SMASH CUT")
                || upper.startsWith("DISSOLVE"))) {
            return "transition";
        }
        if (parenthetical) {
            return "parenthetical";
        }
        return "action";
    }
}

