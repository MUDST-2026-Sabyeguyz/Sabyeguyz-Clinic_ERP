package com.sabyeguyz.clinicv2.commission;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionAllocationLedgerRepository extends JpaRepository<CommissionAllocationLedger, Long> {
    List<CommissionAllocationLedger> findByCourseIdOrderByIdDesc(Long courseId);

    List<CommissionAllocationLedger> findByVisitDateBetweenOrderByVisitDateDescIdDesc(LocalDate from, LocalDate to);
}
