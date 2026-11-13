package com.sabyeguyz.clinicv2.sale;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundLockService {

    private final RefundTransactionRepository refunds;
    private final PaymentLockService paymentLocks;
    private final EntityManager entityManager;

    public RefundLockService(
            RefundTransactionRepository refunds,
            PaymentLockService paymentLocks,
            EntityManager entityManager
    ) {
        this.refunds = refunds;
        this.paymentLocks = paymentLocks;
        this.entityManager = entityManager;
    }

    /**
     * Locks receipt, payment and refund in that order.
     * Reloads refund state before the caller validates a transition.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RefundTransaction lockForUpdate(Long refundId) {
        if (refundId == null) {
            throw new IllegalArgumentException(
                    "ต้องระบุรหัสรายการคืนเงิน"
            );
        }

        RefundTransaction refund = refunds.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ไม่พบรายการคืนเงิน"
                ));

        paymentLocks.lockForUpdate(
                refund.getOriginalPayment().getId()
        );

        entityManager.refresh(
                refund,
                LockModeType.PESSIMISTIC_WRITE
        );

        return refund;
    }
}