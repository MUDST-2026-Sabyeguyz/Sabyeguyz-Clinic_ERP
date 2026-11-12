package com.sabyeguyz.clinicv2.sale;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SingleVisitEntitlementRepository extends JpaRepository<SingleVisitEntitlement, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SingleVisitEntitlement e where e.id = :id")
    Optional<SingleVisitEntitlement> findByIdForUpdate(@Param("id") Long id);

    @Query("select e from SingleVisitEntitlement e where e.transaction.patient.id = :patientId "
            + "and e.transaction.transactionStatus = true "
            + "order by e.transaction.transactionDate desc, e.id desc")
    List<SingleVisitEntitlement> findActiveByPatientId(@Param("patientId") Long patientId);

    Optional<SingleVisitEntitlement> findByTransactionId(Long transactionId);
}
