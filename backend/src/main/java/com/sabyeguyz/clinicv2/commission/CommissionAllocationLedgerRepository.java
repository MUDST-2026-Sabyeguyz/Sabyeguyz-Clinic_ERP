package com.sabyeguyz.clinicv2.commission;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.sabyeguyz.clinicv2.enums.AllocationEntryType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionAllocationLedgerRepository extends JpaRepository<CommissionAllocationLedger, Long> {
    List<CommissionAllocationLedger> findByCourseIdOrderByIdDesc(Long courseId);
    /** Finds ledger activity involving the branch as seller or treatment location. */
    @org.springframework.data.jpa.repository.Query(
            "select a from CommissionAllocationLedger a "
                    + "where a.visitDate between :from and :to "
                    + "and (a.course.branch.id = :branchId "
                    + "or (a.visitUsage.visit.branch.id = :branchId "
                    + "and a.treatmentFeeAmount <> 0)) "
                    + "order by a.id")
    List<CommissionAllocationLedger> findActivityRowsForBranch(
            @org.springframework.data.repository.query.Param("branchId")
            Long branchId,
            @org.springframework.data.repository.query.Param("from")
            LocalDate from,
            @org.springframework.data.repository.query.Param("to")
            LocalDate to);

    List<CommissionAllocationLedger> findByVisitUsageIdOrderByIdAsc(Long visitUsageId);

    /** Finds substitute treatment fees earned at the selected branch. */
    @org.springframework.data.jpa.repository.Query(
            "select a from CommissionAllocationLedger a "
                    + "where a.treatingStaff.id = :staffId "
                    + "and a.visitUsage.visit.branch.id = :branchId "
                    + "and a.treatmentFeeAmount <> 0 "
                    + "and a.visitDate between :from and :to "
                    + "order by a.id")
    List<CommissionAllocationLedger> findTreatingFeeRowsForBranch(
            @org.springframework.data.repository.query.Param("staffId")
            Long staffId,
            @org.springframework.data.repository.query.Param("branchId")
            Long branchId,
            @org.springframework.data.repository.query.Param("from")
            LocalDate from,
            @org.springframework.data.repository.query.Param("to")
            LocalDate to);

    /** Finds owner commission assigned to the course's selling branch. */
    @org.springframework.data.jpa.repository.Query(
            "select a from CommissionAllocationLedger a "
                    + "where a.ownerStaff.id = :staffId "
                    + "and a.course.branch.id = :branchId "
                    + "and a.visitDate between :from and :to "
                    + "order by a.id")
    List<CommissionAllocationLedger> findOwnerRowsForBranch(
            @org.springframework.data.repository.query.Param("staffId")
            Long staffId,
            @org.springframework.data.repository.query.Param("branchId")
            Long branchId,
            @org.springframework.data.repository.query.Param("from")
            LocalDate from,
            @org.springframework.data.repository.query.Param("to")
            LocalDate to);

    /** Rows of the Owner's commission earned on treatments in a date range (Allocation and Reversal). */
    @org.springframework.data.jpa.repository.Query("select a from CommissionAllocationLedger a where a.ownerStaff.id = :staffId "
            + "and a.visitDate between :from and :to order by a.id")
    List<CommissionAllocationLedger> findOwnerRows(@org.springframework.data.repository.query.Param("staffId") Long staffId,
            @org.springframework.data.repository.query.Param("from") LocalDate from,
            @org.springframework.data.repository.query.Param("to") LocalDate to);

    /** Rows whose treatment fee belongs to the treating PT, in a date range. */
    @org.springframework.data.jpa.repository.Query("select a from CommissionAllocationLedger a where a.treatingStaff.id = :staffId "
            + "and a.treatmentFeeAmount <> 0 and a.visitDate between :from and :to order by a.id")
    List<CommissionAllocationLedger> findTreatingFeeRows(@org.springframework.data.repository.query.Param("staffId") Long staffId,
            @org.springframework.data.repository.query.Param("from") LocalDate from,
            @org.springframework.data.repository.query.Param("to") LocalDate to);

    /** Checks paid owner commission for courses sold in the selected branch. */
    default boolean ownerCommissionOfSaleMonthInBranch(
            Long sellerStaffId, String saleMonth, Long branchId,
            List<com.sabyeguyz.clinicv2.enums.PaymentStatus> statuses) {
        return countOwnerPaymentsForBranch(
                sellerStaffId, saleMonth, branchId, statuses) > 0;
    }

    @org.springframework.data.jpa.repository.Query(
            "select count(a) from CommissionAllocationLedger a "
                    + "where a.course.sellerStaff.id = :staffId "
                    + "and a.course.saleMonth = :saleMonth "
                    + "and a.course.branch.id = :branchId "
                    + "and a.ownerMonthlyPayment.paymentStatus in :statuses")
    long countOwnerPaymentsForBranch(
            @org.springframework.data.repository.query.Param("staffId")
            Long staffId,
            @org.springframework.data.repository.query.Param("saleMonth")
            String saleMonth,
            @org.springframework.data.repository.query.Param("branchId")
            Long branchId,
            @org.springframework.data.repository.query.Param("statuses")
            List<com.sabyeguyz.clinicv2.enums.PaymentStatus> statuses);

    default boolean ownerCommissionOfSaleMonthIn(Long sellerStaffId, String saleMonth,
                                                  List<com.sabyeguyz.clinicv2.enums.PaymentStatus> statuses) {
        return countOwnerPayments(sellerStaffId, saleMonth, statuses) > 0;
    }

    /** Owner commission of the courses a seller sold in a month that sits in payments of the given statuses. */
    @org.springframework.data.jpa.repository.Query("select count(a) from CommissionAllocationLedger a "
            + "where a.course.sellerStaff.id = :staffId and a.course.saleMonth = :saleMonth "
            + "and a.ownerMonthlyPayment.paymentStatus in :statuses")
    long countOwnerPayments(@org.springframework.data.repository.query.Param("staffId") Long staffId,
            @org.springframework.data.repository.query.Param("saleMonth") String saleMonth,
            @org.springframework.data.repository.query.Param("statuses") List<com.sabyeguyz.clinicv2.enums.PaymentStatus> statuses);

    Optional<CommissionAllocationLedger> findByVisitUsageIdAndEntryType(Long visitUsageId, AllocationEntryType entryType);

    List<CommissionAllocationLedger> findByVisitDateBetweenOrderByVisitDateDescIdDesc(LocalDate from, LocalDate to);
}
