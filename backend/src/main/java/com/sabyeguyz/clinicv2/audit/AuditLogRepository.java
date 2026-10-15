package com.sabyeguyz.clinicv2.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    long countByTableNameAndActionAndChangedAtGreaterThanEqualAndChangedAtLessThan(
            String tableName, AuditAction action, Instant from, Instant to);
}
