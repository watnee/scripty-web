package com.scripty.viewmodel.project.stats;

import java.util.List;

/**
 * Aggregated screenplay statistics for the project stats page:
 * script size, dialogue/action balance, character and location breakdowns.
 */
public class ScriptStatsViewModel {

    private Integer projectId;
    private String projectTitle;
    private Integer editionId;
    private String editionName;

    private int sceneCount;
    private int pageEstimate;
    private int totalWords;
    private int dialogueWords;
    private int actionWords;
    private int dialoguePercent;
    private int actionPercent;
    private int speakingCharacterCount;
    private int locationCount;

    private int interiorSceneCount;
    private int exteriorSceneCount;
    private int daySceneCount;
    private int nightSceneCount;

    private List<CharacterStatViewModel> characters;
    private List<LocationStatViewModel> locations;

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public Integer getEditionId() {
        return editionId;
    }

    public void setEditionId(Integer editionId) {
        this.editionId = editionId;
    }

    public String getEditionName() {
        return editionName;
    }

    public void setEditionName(String editionName) {
        this.editionName = editionName;
    }

    public int getSceneCount() {
        return sceneCount;
    }

    public void setSceneCount(int sceneCount) {
        this.sceneCount = sceneCount;
    }

    public int getPageEstimate() {
        return pageEstimate;
    }

    public void setPageEstimate(int pageEstimate) {
        this.pageEstimate = pageEstimate;
    }

    public int getTotalWords() {
        return totalWords;
    }

    public void setTotalWords(int totalWords) {
        this.totalWords = totalWords;
    }

    public int getDialogueWords() {
        return dialogueWords;
    }

    public void setDialogueWords(int dialogueWords) {
        this.dialogueWords = dialogueWords;
    }

    public int getActionWords() {
        return actionWords;
    }

    public void setActionWords(int actionWords) {
        this.actionWords = actionWords;
    }

    public int getDialoguePercent() {
        return dialoguePercent;
    }

    public void setDialoguePercent(int dialoguePercent) {
        this.dialoguePercent = dialoguePercent;
    }

    public int getActionPercent() {
        return actionPercent;
    }

    public void setActionPercent(int actionPercent) {
        this.actionPercent = actionPercent;
    }

    public int getSpeakingCharacterCount() {
        return speakingCharacterCount;
    }

    public void setSpeakingCharacterCount(int speakingCharacterCount) {
        this.speakingCharacterCount = speakingCharacterCount;
    }

    public int getLocationCount() {
        return locationCount;
    }

    public void setLocationCount(int locationCount) {
        this.locationCount = locationCount;
    }

    public int getInteriorSceneCount() {
        return interiorSceneCount;
    }

    public void setInteriorSceneCount(int interiorSceneCount) {
        this.interiorSceneCount = interiorSceneCount;
    }

    public int getExteriorSceneCount() {
        return exteriorSceneCount;
    }

    public void setExteriorSceneCount(int exteriorSceneCount) {
        this.exteriorSceneCount = exteriorSceneCount;
    }

    public int getDaySceneCount() {
        return daySceneCount;
    }

    public void setDaySceneCount(int daySceneCount) {
        this.daySceneCount = daySceneCount;
    }

    public int getNightSceneCount() {
        return nightSceneCount;
    }

    public void setNightSceneCount(int nightSceneCount) {
        this.nightSceneCount = nightSceneCount;
    }

    public List<CharacterStatViewModel> getCharacters() {
        return characters;
    }

    public void setCharacters(List<CharacterStatViewModel> characters) {
        this.characters = characters;
    }

    public List<LocationStatViewModel> getLocations() {
        return locations;
    }

    public void setLocations(List<LocationStatViewModel> locations) {
        this.locations = locations;
    }
}
