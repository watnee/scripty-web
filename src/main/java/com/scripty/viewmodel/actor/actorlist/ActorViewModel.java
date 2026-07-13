/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.actor.actorlist;

import com.scripty.viewmodel.person.personlist.CharacterViewModel;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author chris
 */
public class ActorViewModel {
    
    private int id;
    private String first;
    private String last;
    private String phone;
    private String email;
    private boolean hasHeadshot;
    private List<CharacterViewModel> assignedCharacters = new ArrayList<>();
    private List<CharacterViewModel> auditionCharacters = new ArrayList<>();
    private List<Integer> auditionCharacterIds = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirst() {
        return first;
    }

    public void setFirst(String first) {
        this.first = first;
    }

    public String getLast() {
        return last;
    }

    public void setLast(String last) {
        this.last = last;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isHasHeadshot() {
        return hasHeadshot;
    }

    public void setHasHeadshot(boolean hasHeadshot) {
        this.hasHeadshot = hasHeadshot;
    }

    public List<CharacterViewModel> getAssignedCharacters() {
        return assignedCharacters;
    }

    public void setAssignedCharacters(List<CharacterViewModel> assignedCharacters) {
        this.assignedCharacters = assignedCharacters;
    }

    public List<CharacterViewModel> getAuditionCharacters() {
        return auditionCharacters;
    }

    public void setAuditionCharacters(List<CharacterViewModel> auditionCharacters) {
        this.auditionCharacters = auditionCharacters;
    }

    public List<Integer> getAuditionCharacterIds() {
        return auditionCharacterIds;
    }

    public void setAuditionCharacterIds(List<Integer> auditionCharacterIds) {
        this.auditionCharacterIds = auditionCharacterIds;
    }

    public boolean isAuditioningFor(int characterId) {
        return auditionCharacterIds.contains(characterId);
    }

}
