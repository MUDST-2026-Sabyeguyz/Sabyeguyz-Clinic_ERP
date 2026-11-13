package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByReceiptIdOrderByIdAsc(Long receiptId);

    /** Checks whether the receipt already has a completed payment. */
    boolean existsByReceiptIdAndPaymentStatusTrue(Long receiptId);

    @Query("select coalesce(sum(p.amount), 0) from PaymentTransaction p "
            + "where p.receipt.id = :receiptId and p.paymentStatus = true")
    BigDecimal paidAmountOf(@Param("receiptId") Long receiptId);

    /** Completed payments in [from, to); callers supply an exclusive upper bound. */
    @Query("select p from PaymentTransaction p where p.paymentDate >= :from and p.paymentDate < :to "
            + "and p.paymentStatus = true and (:branchId is null or p.branch.id = :branchId)")
    List<PaymentTransaction> findByPaymentDateBetween(@Param("from") Instant from, @Param("to") Instant to,
                                                        @Param("branchId") Long branchId);

    /**
     * Returns the completed payment for a receipt.
     * The database allows at most one completed payment per receipt.
     */
    Optional<PaymentTransaction> findByReceiptIdAndPaymentStatusTrue(
            Long receiptId
    );
}
