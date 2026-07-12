package com.scripty.viewmodel.project.stats;

/**
 * Dialogue footprint of one speaking character: how often they speak,
 * how many words they say, and how many scenes they appear in.
 */
public class CharacterStatViewModel {

    private String name;
    private int speechCount;
    private int wordCount;
    private int sceneCount;
    private int dialogueSharePercent;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSpeechCount() {
        return speechCount;
    }

    public void setSpeechCount(int speechCount) {
        this.speechCount = speechCount;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }

    public int getSceneCount() {
        return sceneCount;
    }

    public void setSceneCount(int sceneCount) {
        this.sceneCount = sceneCount;
    }

    public int getDialogueSharePercent() {
        return dialogueSharePercent;
    }

    public void setDialogueSharePercent(int dialogueSharePercent) {
        this.dialogueSharePercent = dialogueSharePercent;
    }
}
