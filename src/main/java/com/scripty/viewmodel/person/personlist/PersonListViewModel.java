package com.scripty.viewmodel.person.personlist;

import java.util.List;

public class PersonListViewModel {

    private int projectId;
    private String projectTitle;
    private List<CharacterViewModel> characters;

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public List<CharacterViewModel> getCharacters() {
        return characters;
    }

    public void setCharacters(List<CharacterViewModel> characters) {
        this.characters = characters;
    }

}
