package com.scripty.viewmodel.actor.actorlist;

import java.util.ArrayList;
import java.util.List;

public class CastingCharacterViewModel {

    private int id;
    private String name;
    private Integer actorId;
    private String actorName;
    private List<AuditionActorViewModel> auditionActors = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getActorId() {
        return actorId;
    }

    public void setActorId(Integer actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public List<AuditionActorViewModel> getAuditionActors() {
        return auditionActors;
    }

    public void setAuditionActors(List<AuditionActorViewModel> auditionActors) {
        this.auditionActors = auditionActors;
    }
}
