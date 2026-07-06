package com.scripty.viewmodel.project.versionhistory;

import java.util.ArrayList;
import java.util.List;

public class VersionChangeSummary {

    private static final int MAX_DETAILS = 5;

    private int blocksAdded;
    private int blocksRemoved;
    private int blocksEdited;
    private int scenesAdded;
    private int scenesRemoved;
    private int scenesRenamed;
    private int charactersAdded;
    private int charactersRemoved;
    private boolean projectMetadataChanged;
    private final List<String> details = new ArrayList<>();

    public int getBlocksAdded() {
        return blocksAdded;
    }

    public void setBlocksAdded(int blocksAdded) {
        this.blocksAdded = blocksAdded;
    }

    public int getBlocksRemoved() {
        return blocksRemoved;
    }

    public void setBlocksRemoved(int blocksRemoved) {
        this.blocksRemoved = blocksRemoved;
    }

    public int getBlocksEdited() {
        return blocksEdited;
    }

    public void setBlocksEdited(int blocksEdited) {
        this.blocksEdited = blocksEdited;
    }

    public int getScenesAdded() {
        return scenesAdded;
    }

    public void setScenesAdded(int scenesAdded) {
        this.scenesAdded = scenesAdded;
    }

    public int getScenesRemoved() {
        return scenesRemoved;
    }

    public void setScenesRemoved(int scenesRemoved) {
        this.scenesRemoved = scenesRemoved;
    }

    public int getScenesRenamed() {
        return scenesRenamed;
    }

    public void setScenesRenamed(int scenesRenamed) {
        this.scenesRenamed = scenesRenamed;
    }

    public int getCharactersAdded() {
        return charactersAdded;
    }

    public void setCharactersAdded(int charactersAdded) {
        this.charactersAdded = charactersAdded;
    }

    public int getCharactersRemoved() {
        return charactersRemoved;
    }

    public void setCharactersRemoved(int charactersRemoved) {
        this.charactersRemoved = charactersRemoved;
    }

    public boolean isProjectMetadataChanged() {
        return projectMetadataChanged;
    }

    public void setProjectMetadataChanged(boolean projectMetadataChanged) {
        this.projectMetadataChanged = projectMetadataChanged;
    }

    public List<String> getDetails() {
        return details;
    }

    public void addDetail(String detail) {
        details.add(detail);
    }

    public boolean hasChanges() {
        return blocksAdded > 0
                || blocksRemoved > 0
                || blocksEdited > 0
                || scenesAdded > 0
                || scenesRemoved > 0
                || scenesRenamed > 0
                || charactersAdded > 0
                || charactersRemoved > 0
                || projectMetadataChanged;
    }

    public List<String> getVisibleDetails() {
        if (details.isEmpty()) {
            return details;
        }
        if (details.size() <= MAX_DETAILS) {
            return details;
        }
        List<String> visible = new ArrayList<>(details.subList(0, MAX_DETAILS));
        visible.add("and " + (details.size() - MAX_DETAILS) + " more changes");
        return visible;
    }

    public String getCompactSummary() {
        if (!hasChanges()) {
            return "No changes";
        }
        List<String> parts = new ArrayList<>();
        if (blocksAdded > 0) {
            parts.add("+" + blocksAdded + " block" + plural(blocksAdded));
        }
        if (blocksRemoved > 0) {
            parts.add("-" + blocksRemoved + " block" + plural(blocksRemoved));
        }
        if (blocksEdited > 0) {
            parts.add(blocksEdited + " block edit" + plural(blocksEdited));
        }
        if (scenesAdded > 0) {
            parts.add("+" + scenesAdded + " scene" + plural(scenesAdded));
        }
        if (scenesRemoved > 0) {
            parts.add("-" + scenesRemoved + " scene" + plural(scenesRemoved));
        }
        if (scenesRenamed > 0) {
            parts.add(scenesRenamed + " scene rename" + plural(scenesRenamed));
        }
        if (charactersAdded > 0) {
            parts.add("+" + charactersAdded + " character" + plural(charactersAdded));
        }
        if (charactersRemoved > 0) {
            parts.add("-" + charactersRemoved + " character" + plural(charactersRemoved));
        }
        if (projectMetadataChanged) {
            parts.add("project details");
        }
        return String.join(", ", parts);
    }

    private static String plural(int count) {
        return count == 1 ? "" : "s";
    }
}
