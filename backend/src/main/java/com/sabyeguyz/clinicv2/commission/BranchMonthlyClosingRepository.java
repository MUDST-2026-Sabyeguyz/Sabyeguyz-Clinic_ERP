package com.sabyeguyz.clinicv2.commission;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchMonthlyClosingRepository extends JpaRepository<BranchMonthlyClosing, Long> {
        Optional<BranchMonthlyClosing> findByBranchIdAndClosingMonth(
                Long branchId, String closingMonth);
}
