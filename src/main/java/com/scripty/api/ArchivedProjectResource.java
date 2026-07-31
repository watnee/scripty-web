package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of an archived screenplay.
 *
 * <p>Shaped like {@link TrashedProjectResource} but with no purge date, because
 * nothing expires out of the archive — the absence of the field is the
 * distinction, not a null. It carries {@code lastEdited} and the teams as well,
 * which a trashed project does not: the archive is a list you browse for a
 * finished production rather than one you clear, so it is worth showing the
 * same detail the project list shows.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.ARCHIVED_PROJECT, collectionRelation = ApiRel.ARCHIVED_PROJECTS)
public class ArchivedProjectResource extends RepresentationModel<ArchivedProjectResource> {

    private Integer id;
    private String title;
    private OffsetDateTime lastEdited;
    private OffsetDateTime archivedAt;
    private List<String> teams;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public OffsetDateTime getLastEdited() {
        return lastEdited;
    }

    public void setLastEdited(OffsetDateTime lastEdited) {
        this.lastEdited = lastEdited;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public List<String> getTeams() {
        return teams;
    }

    public void setTeams(List<String> teams) {
        this.teams = teams;
    }
}
