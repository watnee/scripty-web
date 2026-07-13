package com.scripty.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_activity")
public class ProjectActivity {

    public static final String ACTION_PROJECT_CREATED = "PROJECT_CREATED";
    public static final String ACTION_PROJECT_RENAMED = "PROJECT_RENAMED";
    public static final String ACTION_PROJECT_TRASHED = "PROJECT_TRASHED";
    public static final String ACTION_PROJECT_RESTORED = "PROJECT_RESTORED";
    public static final String ACTION_TITLE_PAGE_UPDATED = "TITLE_PAGE_UPDATED";
    public static final String ACTION_TEAMS_UPDATED = "TEAMS_UPDATED";
    public static final String ACTION_SCRIPT_EDITED = "SCRIPT_EDITED";
    public static final String ACTION_SCRIPT_IMPORTED = "SCRIPT_IMPORTED";
    public static final String ACTION_VERSION_SAVED = "VERSION_SAVED";
    public static final String ACTION_VERSION_RESTORED = "VERSION_RESTORED";
    public static final String ACTION_DOCUMENT_CREATED = "DOCUMENT_CREATED";
    public static final String ACTION_DOCUMENT_UPDATED = "DOCUMENT_UPDATED";
    public static final String ACTION_DOCUMENT_DELETED = "DOCUMENT_DELETED";
    public static final String ACTION_DOCUMENT_INSERTED = "DOCUMENT_INSERTED";
    public static final String ACTION_INVITATION_SENT = "INVITATION_SENT";
    public static final String ACTION_INVITATION_ACCEPTED = "INVITATION_ACCEPTED";
    public static final String ACTION_INVITATION_REVOKED = "INVITATION_REVOKED";
    public static final String ACTION_CHARACTER_CREATED = "CHARACTER_CREATED";
    public static final String ACTION_CHARACTER_DELETED = "CHARACTER_DELETED";
    public static final String ACTION_ACTOR_ASSIGNED = "ACTOR_ASSIGNED";
    public static final String ACTION_ACTOR_ADDED = "ACTOR_ADDED";
    public static final String ACTION_ACTOR_UPDATED = "ACTOR_UPDATED";
    public static final String ACTION_ACTOR_REMOVED = "ACTOR_REMOVED";
    public static final String ACTION_AUDITIONS_UPDATED = "AUDITIONS_UPDATED";
    public static final String ACTION_SCRIPT_UNDO = "SCRIPT_UNDO";
    public static final String ACTION_SCRIPT_REDO = "SCRIPT_REDO";

    public static final String ENTITY_PROJECT = "PROJECT";
    public static final String ENTITY_BLOCK = "BLOCK";
    public static final String ENTITY_VERSION = "VERSION";
    public static final String ENTITY_DOCUMENT = "DOCUMENT";
    public static final String ENTITY_INVITATION = "INVITATION";
    public static final String ENTITY_PERSON = "PERSON";
    public static final String ENTITY_ACTOR = "ACTOR";
    public static final String ENTITY_AUDITION = "AUDITION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Integer entityId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public User getActorUser() {
        return actorUser;
    }

    public void setActorUser(User actorUser) {
        this.actorUser = actorUser;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
