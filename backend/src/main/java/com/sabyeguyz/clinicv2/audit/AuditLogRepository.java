package com.sabyeguyz.clinicv2.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    long countByTableNameAndActionAndChangedAtGreaterThanEqualAndChangedAtLessThan(
            String tableName, AuditAction action, Instant from, Instant to);

    /** Course expiry changes reuse the existing audit store, without a schema migration. */
    List<AuditLog> findByTableNameAndRecordIdOrderByChangedAtDesc(String tableName, String recordId);

    /** The tables that have audit rows, for the filter of the audit page. */
    @Query("select distinct a.tableName from AuditLog a order by a.tableName")
    List<String> findDistinctTableNames();
}
