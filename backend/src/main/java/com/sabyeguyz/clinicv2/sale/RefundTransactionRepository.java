package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundTransactionRepository extends JpaRepository<RefundTransaction, Long> {
    List<RefundTransaction> findByOriginalPaymentId(Long paymentId);

    /** Refunds that still count against a payment: everything except Failed and Voided. */
    @Query("select coalesce(sum(r.refundAmount), 0) from RefundTransaction r "
            + "where r.originalPayment.id = :paymentId and r.refundStatus in "
            + "(com.sabyeguyz.clinicv2.enums.RefundStatus.Pending, com.sabyeguyz.clinicv2.enums.RefundStatus.Completed)")
    BigDecimal refundedOrPendingAmountOf(@Param("paymentId") Long paymentId);
}
