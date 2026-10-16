package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.enums.RefundStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundTransactionRepository extends JpaRepository<RefundTransaction, Long> {
    List<RefundTransaction> findByOriginalPaymentId(Long paymentId);

    /** Refunds that still count against a payment: everything except Failed and Voided. */
    default BigDecimal refundedOrPendingAmountOf(Long paymentId) {
        return sumRefundAmount(paymentId, List.of(RefundStatus.Pending, RefundStatus.Completed));
    }

    /** The status list is a parameter: a PostgreSQL enum literal in JPQL is cast to the wrong type name. */
    @Query("select coalesce(sum(r.refundAmount), 0) from RefundTransaction r "
            + "where r.originalPayment.id = :paymentId and r.refundStatus in :statuses")
    BigDecimal sumRefundAmount(@Param("paymentId") Long paymentId, @Param("statuses") List<RefundStatus> statuses);
}
