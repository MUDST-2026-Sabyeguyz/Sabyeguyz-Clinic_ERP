package com.sabyeguyz.clinicv2.sale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesTransactionRepository extends JpaRepository<SalesTransaction, Long> {
    @Query("select t from SalesTransaction t where "
            + "(:patientId is null or t.patient.id = :patientId) "
            + "and (:branchId is null or t.branch.id = :branchId)")
    Page<SalesTransaction> search(@Param("patientId") Long patientId, @Param("branchId") Long branchId, Pageable pageable);
}
