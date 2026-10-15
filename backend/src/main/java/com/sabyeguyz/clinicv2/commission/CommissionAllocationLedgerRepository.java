package com.sabyeguyz.clinicv2.commission;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.sabyeguyz.clinicv2.enums.AllocationEntryType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionAllocationLedgerRepository extends JpaRepository<CommissionAllocationLedger, Long> {
    List<CommissionAllocationLedger> findByCourseIdOrderByIdDesc(Long courseId);

    List<CommissionAllocationLedger> findByVisitUsageIdOrderByIdAsc(Long visitUsageId);

    Optional<CommissionAllocationLedger> findByVisitUsageIdAndEntryType(Long visitUsageId, AllocationEntryType entryType);

    List<CommissionAllocationLedger> findByVisitDateBetweenOrderByVisitDateDescIdDesc(LocalDate from, LocalDate to);
}
