package com.sabyeguyz.clinic.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.*;

public interface CommissionAllocationLedgerRepository
        extends JpaRepository<CommissionAllocationLedger, Long> {
    List<CommissionAllocationLedger> findByCoursePurchaseIdOrderByVisitDateAsc(Long purchaseId);

    List<CommissionAllocationLedger> findByVisitDateBetweenOrderByVisitDateDescIdDesc(
            LocalDate from, LocalDate to);
}
