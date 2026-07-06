package io.github.forrestknight.buoy.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Immutable record of one mutation. Ids and keys are stored as plain values —
 * deliberately no foreign keys, so history survives entity deletion.
 */
@Entity
@Immutable
@Table(name = "audit_log_entry")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "entity_key")
    private String entityKey;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "environment_id")
    private Long environmentId;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode diff;

    private String source;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(ActorType actorType, String actorName, AuditAction action, String entityType,
                         Long entityId, String entityKey, Long projectId, Long environmentId,
                         JsonNode diff, String source) {
        this.actorType = actorType;
        this.actorName = actorName;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityKey = entityKey;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.diff = diff;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getActorName() {
        return actorName;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getEntityKey() {
        return entityKey;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getEnvironmentId() {
        return environmentId;
    }

    public JsonNode getDiff() {
        return diff;
    }

    public String getSource() {
        return source;
    }
}
