package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sabyeguyz.clinicv2.enums.ReceiptStatus;
import com.sabyeguyz.clinicv2.enums.TransactionType;

public interface SalesTransactionRepository extends JpaRepository<SalesTransaction, Long> {
    long countByReceiptId(Long receiptId);

    List<SalesTransaction> findByVisitId(Long visitId);

    List<SalesTransaction> findByReceiptId(Long receiptId);

    @Query("select t from SalesTransaction t where "
            + "(:patientId is null or t.patient.id = :patientId) "
            + "and (:branchId is null or t.branch.id = :branchId)")
    Page<SalesTransaction> search(
            @Param("patientId") Long patientId,
            @Param("branchId") Long branchId,
            Pageable pageable);

    /** Select one legacy line ID per receipt before applying pagination. */
    @Query("select t from SalesTransaction t where "
            + "(:patientId is null or t.patient.id = :patientId) "
            + "and (:branchId is null or t.branch.id = :branchId) "
            + "and t.id = (select min(line.id) from SalesTransaction line where line.receipt.id = t.receipt.id)")
    Page<SalesTransaction> searchReceipts(
            @Param("patientId") Long patientId,
            @Param("branchId") Long branchId,
            Pageable pageable);

    @Query("select t from SalesTransaction t where t.transactionDate between :from and :to "
            + "and (:branchId is null or t.branch.id = :branchId)")
    List<SalesTransaction> findByTransactionDateBetween(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("branchId") Long branchId);

    @Query("""
        select t
        from SalesTransaction t
        where t.transactionType = :type
          and t.visit.visitDate >= :from
          and t.visit.visitDate < :to
        """)
    List<SalesTransaction> findByVisitDateBetween(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("type") TransactionType type);

    /**
     * Returns all sale lines in document order, including voided lines.
     * Historical receipt details must preserve each line's status.
     */
    @Query("""
        select t
        from SalesTransaction t
        where t.receipt.id = :receiptId
        order by t.lineNo asc, t.id asc
    """)
    List<SalesTransaction> findReceiptLines(
            @Param("receiptId") Long receiptId
    );

    /**
     * Returns net sales from effective sale lines on one receipt.
     * Existing-course collection items do not create new sales.
     */
    default BigDecimal netSalesOfReceipt(Long receiptId) {
        return sumNetSalesOfReceipt(receiptId, ReceiptStatus.Voided);
    }

    @Query("""
        select coalesce(
            sum(t.amount - t.allocatedBillDiscountAmount),
            0
        )
        from SalesTransaction t
        where t.receipt.id = :receiptId
          and t.transactionStatus = true
          and t.receipt.receiptStatus <> :voidedStatus
        """)
    BigDecimal sumNetSalesOfReceipt(
            @Param("receiptId") Long receiptId,
            @Param("voidedStatus") ReceiptStatus voidedStatus);
}
