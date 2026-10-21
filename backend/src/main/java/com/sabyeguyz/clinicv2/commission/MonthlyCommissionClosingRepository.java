package com.sabyeguyz.clinicv2.commission;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyCommissionClosingRepository extends JpaRepository<MonthlyCommissionClosing, Long> {
    Optional<MonthlyCommissionClosing> findByClosingMonthAndStaffIdAndBranchId(
            String closingMonth, Long staffId, Long branchId);

    List<MonthlyCommissionClosing> findByClosingMonthAndBranchId(
            String closingMonth, Long branchId);

    List<MonthlyCommissionClosing> findByClosingMonth(String closingMonth);

    boolean existsByCommissionSchemeId(Long commissionSchemeId);

    /** Locks the branch before acquiring any monthly lock. */
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT 1 FROM pg_advisory_xact_lock("
                    + "hashtext('commission-branch-gate'), "
                    + "hashtext(CAST(:branchId AS text)))",
            nativeQuery = true)
    Integer lockBranch(
            @org.springframework.data.repository.query.Param("branchId")
            Long branchId);

    /** Acquires the monthly lock after the branch lock. */
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT 1 FROM pg_advisory_xact_lock("
                    + "hashtext('commission-branch:' || CAST(:branchId AS text)), "
                    + "hashtext(CAST(:month AS text)))",
            nativeQuery = true)
    Integer lockBranchMonthKey(
            @org.springframework.data.repository.query.Param("branchId")
            Long branchId,
            @org.springframework.data.repository.query.Param("month")
            String month);

    /** Uses the same lock order for closing, payment and reopening. */
    default Integer lockBranchMonth(Long branchId, String month) {
        lockBranch(branchId);
        return lockBranchMonthKey(branchId, month);
    }
}
