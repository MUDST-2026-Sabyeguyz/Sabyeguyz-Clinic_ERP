package com.sabyeguyz.clinic.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long actorId;
    private String action;
    private String entityType;
    private Long entityId;
    private Instant createdAt;
    protected AuditLog() {}
    AuditLog(Long actorId, String action, String entityType, Long entityId) {
        this.actorId = actorId; this.action = action; this.entityType = entityType;
        this.entityId = entityId; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public Instant getCreatedAt() { return createdAt; }
}
