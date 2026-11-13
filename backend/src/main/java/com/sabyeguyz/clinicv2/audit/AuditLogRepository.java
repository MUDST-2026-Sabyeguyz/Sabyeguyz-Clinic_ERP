package com.sabyeguyz.clinicv2.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.sabyeguyz.clinicv2.enums.AuditAction;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    long countByTableNameAndActionAndChangedAtGreaterThanEqualAndChangedAtLessThan(
            String tableName,
            AuditAction action,
            Instant from,
            Instant to);

    /** The tables that have audit rows, for the filter of the audit page. */
    @Query("select distinct a.tableName from AuditLog a order by a.tableName")
    List<String> findDistinctTableNames();

    List<AuditLog> findByTableNameAndRecordIdAndActionOrderByIdAsc(
            String tableName,
            String recordId,
            AuditAction action);
}
