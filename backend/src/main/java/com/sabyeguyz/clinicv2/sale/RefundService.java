package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.enums.RefundStatus;
import com.sabyeguyz.clinicv2.role.Permission;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refund requests against a completed payment: Pending, then Completed (money
 * handed back) or Failed. Pending and Completed refunds together may never
 * exceed the payment's amount.
 */
@org.springframework.stereotype.Service
public class RefundService {
    private final RefundTransactionRepository refunds;
    private final TransactionCancellationService cancellationService;

    public RefundService(RefundTransactionRepository refunds, TransactionCancellationService cancellationService) {
        this.refunds = refunds;
        this.cancellationService = cancellationService;
    }

    @Transactional
    public RefundTransaction request(PaymentTransaction payment, BigDecimal amount, PaymentMethod method,
                                      Permission permission, String reason, Long actorId) {
        require(payment != null, "ต้องระบุรายการชำระเงิน");
        require(payment.isPaymentStatus(), "รายการชำระเงินนี้ถูกยกเลิกแล้ว ไม่สามารถคืนเงินได้");
        require(method != null, "ต้องระบุช่องทางคืนเงิน");
        require(amount != null && amount.signum() > 0, "จำนวนเงินที่คืนต้องมากกว่า 0");
        BigDecimal alreadyRefunded = refunds.refundedOrPendingAmountOf(payment.getId());
        require(alreadyRefunded.add(amount).compareTo(payment.getAmount()) <= 0,
                "จำนวนเงินที่คืนรวมกันต้องไม่เกินยอดที่ชำระ");

        TransactionCancellation cancellation = cancellationService.recordRefundCancellation(payment, permission,
                reason, actorId);
        RefundTransaction refund = new RefundTransaction();
        refund.setCancellation(cancellation);
        refund.setOriginalPayment(payment);
        refund.setRefundAmount(amount);
        refund.setPaymentMethod(method);
        refund.setRefundStatus(RefundStatus.Pending);
        refund.setRequestedAt(Instant.now());
        refund.setRequestedBy(actorId);
        return refunds.save(refund);
    }

    @Transactional
    public RefundTransaction complete(Long refundId, Long actorId) {
        RefundTransaction refund = pending(refundId);
        refund.setRefundStatus(RefundStatus.Completed);
        refund.setRefundedAt(Instant.now());
        refund.setRefundedBy(actorId);
        return refunds.save(refund);
    }

    @Transactional
    public RefundTransaction fail(Long refundId) {
        RefundTransaction refund = pending(refundId);
        refund.setRefundStatus(RefundStatus.Failed);
        return refunds.save(refund);
    }

    private RefundTransaction pending(Long refundId) {
        RefundTransaction refund = refunds.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการคืนเงิน"));
        require(refund.getRefundStatus() == RefundStatus.Pending, "รายการคืนเงินนี้ไม่ได้อยู่ในสถานะรอดำเนินการ");
        return refund;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
