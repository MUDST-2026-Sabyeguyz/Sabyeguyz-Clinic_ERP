package com.sabyeguyz.clinic.databasev2.audit;

import java.time.Instant;

public record DatabaseV2AuditLogDto(
        Long id,
        Long auditId,
        Long actorId,
        Long changedBy,
        String action,
        String entityType,
        String tableName,
        String entityId,
        String recordId,
        Instant createdAt,
        Instant changedAt,
        String oldValue,
        String newValue
) {
    public static DatabaseV2AuditLogDto of(
            Long auditId,
            String tableName,
            String recordId,
            String action,
            String oldValue,
            String newValue,
            Long changedBy,
            Instant changedAt
    ) {
        return new DatabaseV2AuditLogDto(
                auditId,
                auditId,
                changedBy,
                changedBy,
                action,
                tableName,
                tableName,
                recordId,
                recordId,
                changedAt,
                changedAt,
                oldValue,
                newValue
        );
    }
}
