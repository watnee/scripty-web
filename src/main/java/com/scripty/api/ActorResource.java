package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.ACTOR, collectionRelation = ApiRel.ACTORS)
public class ActorResource extends RepresentationModel<ActorResource> {

    private Integer id;
    private String first;
    private String last;
    private String phone;
    private String email;
    private Boolean hasHeadshot;
    private List<Integer> projectIds;
    /** The characters this actor auditions for, in the project the list was
     * scoped to. Present (possibly empty) only on a project-scoped actor; null
     * — and so omitted — otherwise, since auditions have no meaning without a
     * project. */
    private List<Integer> auditionCharacterIds;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public Boolean getHasHeadshot() {
        return hasHeadshot;
    }

    public void setHasHeadshot(Boolean hasHeadshot) {
        this.hasHeadshot = hasHeadshot;
    }

    public List<Integer> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(List<Integer> projectIds) {
        this.projectIds = projectIds;
    }

    public List<Integer> getAuditionCharacterIds() {
        return auditionCharacterIds;
    }

    public void setAuditionCharacterIds(List<Integer> auditionCharacterIds) {
        this.auditionCharacterIds = auditionCharacterIds;
    }
}
