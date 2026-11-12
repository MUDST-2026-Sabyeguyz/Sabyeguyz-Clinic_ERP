package com.sabyeguyz.clinicv2.catalog;

import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.enums.ReceiptStatus;
import com.sabyeguyz.clinicv2.enums.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCourseNo(String courseNo);

    /** Live courses sold in or before the month whose commission is not locked by a monthly close yet. */
    default long countOpenCommissionUpToMonth(String saleMonth) {
        return countByCommissionStatusUpToMonth(saleMonth, com.sabyeguyz.clinicv2.enums.CommissionStatus.Provisional,
                List.of(CourseStatus.Active, CourseStatus.Completed));
    }

    @Query("select count(c) from Course c where c.saleMonth <= :saleMonth and c.commissionStatus = :status "
            + "and c.courseStatus in :live")
    long countByCommissionStatusUpToMonth(@Param("saleMonth") String saleMonth,
                                           @Param("status") com.sabyeguyz.clinicv2.enums.CommissionStatus status,
                                           @Param("live") java.util.Collection<CourseStatus> live);

    /**
     * Loads a course with a row lock (SELECT ... FOR UPDATE) so concurrent visit usages of the same
     * course queue up and each sees the balance left by the previous one. Call it before anything
     * else reads the course in the transaction: a course already loaded keeps its earlier state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);

    @Query("select c.id from Course c where c.receipt.id = :receiptId order by c.id")
    List<Long> findIdsByReceiptId(@Param("receiptId") Long receiptId);

    long countByReceiptId(Long receiptId);

    Optional<Course> findBySaleTransactionId(Long saleTransactionId);

    boolean existsBySaleTransactionId(Long saleTransactionId);

    List<Course> findBySellerStaffIdAndSaleMonth(Long sellerStaffId, String saleMonth);

    @Query("select c.id from Course c where c.sellerStaff.id = :staffId and c.saleMonth = :saleMonth order by c.id")
    List<Long> findIdsBySellerStaffIdAndSaleMonth(@Param("staffId") Long staffId, @Param("saleMonth") String saleMonth);

    /**
     * Commissionable sales credited to the seller in a sale_month (yyyy-mm), for the Commission Tier: only course
     * purchases still in effect (the sale line, the receipt and the course are not voided or cancelled).
     */
    default BigDecimal sumCreditedSalesByStaffAndSaleMonth(Long staffId, String saleMonth) {
        return sumCreditedSales(staffId, saleMonth, ReceiptStatus.Voided, CourseStatus.Cancelled);
    }

    /** The enum values are parameters: a PostgreSQL enum literal in JPQL is cast to the wrong type name. */
    @Query("select coalesce(sum(c.coursePrice), 0) from Course c "
            + "where c.sellerStaff.id = :staffId and c.saleMonth = :saleMonth "
            + "and c.saleTransaction.transactionStatus = true "
            + "and c.receipt.receiptStatus <> :voided and c.courseStatus <> :cancelled")
    BigDecimal sumCreditedSales(@Param("staffId") Long staffId, @Param("saleMonth") String saleMonth,
                                @Param("voided") ReceiptStatus voided, @Param("cancelled") CourseStatus cancelled);

    /**
     * Refund amounts of Completed refunds against the seller's courses of that sale month, each refund item once. A
     * course whose sale was voided or cancelled is already out of the sales above, so it is not deducted again.
     */
    default BigDecimal sumCompletedRefundsByStaffAndSaleMonth(Long staffId, String saleMonth) {
        return sumRefunds(staffId, saleMonth, RefundStatus.Completed, ReceiptStatus.Voided, CourseStatus.Cancelled);
    }

    @Query("select coalesce(sum(i.refundAmount), 0) from CourseRefundItem i join i.course c "
            + "where c.sellerStaff.id = :staffId and c.saleMonth = :saleMonth "
            + "and i.refund.refundStatus = :refundStatus "
            + "and c.saleTransaction.transactionStatus = true "
            + "and c.receipt.receiptStatus <> :voided and c.courseStatus <> :cancelled")
    BigDecimal sumRefunds(@Param("staffId") Long staffId, @Param("saleMonth") String saleMonth,
                          @Param("refundStatus") RefundStatus refundStatus, @Param("voided") ReceiptStatus voided,
                          @Param("cancelled") CourseStatus cancelled);

    boolean existsByCommissionSchemeId(Long commissionSchemeId);

    /** Whether a course of the scheme was sold after the given date (the scheme may not end before it). */
    @Query("select count(c) > 0 from Course c where c.commissionScheme.id = :schemeId "
            + "and cast(c.receipt.saleDate as date) > :date")
    boolean existsBySchemeUsedAfter(@Param("schemeId") Long schemeId, @Param("date") LocalDate date);

    /** Every Course a patient can use: as the purchaser (implicit Owner) or as a Shared Member. */
    @Query("select distinct c from Course c left join CourseSharedMember m on m.course = c "
            + "where c.purchaserPatient.id = :patientId or m.patient.id = :patientId")
    List<Course> findByPatientId(@Param("patientId") Long patientId);

    @Query("select c from Course c where cast(c.receipt.saleDate as date) between :from and :to")
    List<Course> findByReceiptSaleDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Courses sold in the range whose sale is still in effect (sale line, receipt and course not voided or cancelled). */
    default List<Course> findValidByReceiptSaleDateBetween(LocalDate from, LocalDate to) {
        return findValidSoldBetween(from, to, ReceiptStatus.Voided, CourseStatus.Cancelled);
    }

    @Query("select c from Course c where cast(c.receipt.saleDate as date) between :from and :to "
            + "and c.saleTransaction.transactionStatus = true "
            + "and c.receipt.receiptStatus <> :voided and c.courseStatus <> :cancelled")
    List<Course> findValidSoldBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                      @Param("voided") ReceiptStatus voided, @Param("cancelled") CourseStatus cancelled);

    default List<Long> findDistinctCreditedStaffIdsBySaleMonth(String saleMonth) {
        return findDistinctCreditedStaffIds(saleMonth, ReceiptStatus.Voided, CourseStatus.Cancelled);
    }

    @Query("select distinct c.sellerStaff.id from Course c where c.saleMonth = :saleMonth "
            + "and c.saleTransaction.transactionStatus = true "
            + "and c.receipt.receiptStatus <> :voided and c.courseStatus <> :cancelled")
    List<Long> findDistinctCreditedStaffIds(@Param("saleMonth") String saleMonth,
                                            @Param("voided") ReceiptStatus voided, @Param("cancelled") CourseStatus cancelled);
}
