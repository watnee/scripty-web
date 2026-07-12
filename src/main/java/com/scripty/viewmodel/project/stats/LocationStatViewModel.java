package com.scripty.viewmodel.project.stats;

/**
 * How many scenes take place at one location, parsed from scene headings.
 */
public class LocationStatViewModel {

    private String name;
    private int sceneCount;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSceneCount() {
        return sceneCount;
    }

    public void setSceneCount(int sceneCount) {
        this.sceneCount = sceneCount;
    }
}
