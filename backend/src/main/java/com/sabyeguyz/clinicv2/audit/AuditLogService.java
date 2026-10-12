package com.sabyeguyz.clinicv2.audit;

import com.sabyeguyz.clinicv2.enums.AuditAction;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generic row-level audit trail (Clinic ERP Workflow Brief §9 "System
 * Thinking": every transaction must be traceable back to who/when/what
 * changed). Not yet wired into any of the other v2 services — each domain
 * service currently writes its own purpose-built audit rows (Appointment_
 * status_history, Course_ledger, Commission_allocation_ledger, Commission_
 * rate_override_audit) instead. This is here for changes that don't have
 * one of those dedicated tables.
 */
@Service
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditLog record(String tableName, String recordId, AuditAction action, String oldValue,
                            String newValue, Long actorId) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName ห้ามว่าง");
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException("recordId ห้ามว่าง");
        }
        if (action == null) {
            throw new IllegalArgumentException("ต้องระบุ action");
        }
        AuditLog log = new AuditLog();
        log.setTableName(tableName);
        log.setRecordId(recordId);
        log.setAction(action);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setChangedBy(actorId);
        log.setChangedAt(Instant.now());
        return repository.save(log);
    }
}
