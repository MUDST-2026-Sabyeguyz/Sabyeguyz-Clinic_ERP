package com.sabyeguyz.clinic.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository logs;
    private final TransactionActor actor;
    public AuditService(AuditLogRepository logs, TransactionActor actor) { this.logs = logs; this.actor = actor; }

    // Failure must roll back the business operation; no separate audit transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Long entityId) {
        logs.save(new AuditLog(actor.currentId(), action, entityType, entityId));
    }
}
