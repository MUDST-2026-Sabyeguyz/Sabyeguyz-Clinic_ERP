package com.sabyeguyz.clinicv2.sale;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentLockService {

    private final ReceiptRepository receipts;
    private final PaymentTransactionRepository payments;
    private final EntityManager entityManager;

    public PaymentLockService(
            ReceiptRepository receipts,
            PaymentTransactionRepository payments,
            EntityManager entityManager) {
        this.receipts = receipts;
        this.payments = payments;
        this.entityManager = entityManager;
    }

    /**
     * Locks the receipt before the payment and reloads payment state.
     * The caller must hold an active transaction and validate access.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentTransaction lockForUpdate(Long paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException(
                    "ต้องระบุรหัสรายการชำระเงิน"
            );
        }

        PaymentTransaction payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ไม่พบรายการชำระเงิน"
                ));

        Long receiptId = payment.getReceipt().getId();

        receipts.findByIdForUpdate(receiptId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ไม่พบใบเสร็จของรายการชำระเงิน"
                ));

        // Reload cached state after acquiring the receipt lock.
        entityManager.refresh(
                payment,
                LockModeType.PESSIMISTIC_WRITE
        );

        return payment;
    }
}
