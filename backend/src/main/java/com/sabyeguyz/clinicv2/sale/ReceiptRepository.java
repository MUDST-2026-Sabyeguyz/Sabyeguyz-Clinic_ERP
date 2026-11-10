package com.sabyeguyz.clinicv2.sale;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Receipt r where r.id = :id")
    Optional<Receipt> findByIdForUpdate(@Param("id") Long id);

    Optional<Receipt> findByReceiptNo(String receiptNo);
    Optional<Receipt> findByReceiptNoAndBranchId(String receiptNo, Long branchId);

    /** Highest running number in receipt_no (RE-xxxxxxx), 0 when there is no receipt yet. */
    @Query("select coalesce(max(cast(substring(r.receiptNo, 3) as integer)), 0) from Receipt r")
    int findMaxReceiptSequence();

    @Query(value = "select next_receipt_running_number(:branchId, :month)", nativeQuery = true)
    int nextRunningNumber(@Param("branchId") Long branchId, @Param("month") String month);

    @Query("select r from Receipt r where cast(r.saleDate as date) between :from and :to "
            + "and (:branchId is null or r.branch.id = :branchId)")
    List<Receipt> findBySaleDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                         @Param("branchId") Long branchId);

    /**
     * Returns the original purchase receipt and subsequent collection receipts.
     * Includes voided and refunded documents for historical traceability.
     */
    @Query("""
        select r
        from Receipt r
        where exists (
            select c.id
            from Course c
            where c.id = :courseId
              and c.receipt.id = r.id
        )
        or exists (
            select p.course.id
            from ReceiptCoursePayment p
            where p.course.id = :courseId
              and p.receipt.id = r.id
        )
        order by r.saleDate asc, r.id asc
    """)
    List<Receipt> findCourseReceiptHistory(
            @Param("courseId") Long courseId
    );

    /**
     * Lists receipts independently of sale lines.
     * Includes original purchases and existing-course collections.
     * The caller must resolve branch access before querying.
     */
    @Query(
            value = """
            select r
            from Receipt r
            where (:patientId is null or r.patient.id = :patientId)
              and (:branchId is null or r.branch.id = :branchId)
            order by r.saleDate desc, r.id desc
            """,
            countQuery = """
            select count(r)
            from Receipt r
            where (:patientId is null or r.patient.id = :patientId)
              and (:branchId is null or r.branch.id = :branchId)
            """
    )
    Page<Receipt> searchReceiptHistory(
            @Param("patientId") Long patientId,
            @Param("branchId") Long branchId,
            Pageable pageable
    );
}
