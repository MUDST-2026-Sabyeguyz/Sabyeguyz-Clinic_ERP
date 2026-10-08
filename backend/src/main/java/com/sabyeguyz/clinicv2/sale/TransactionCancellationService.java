package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.course.CourseTransfer;
import com.sabyeguyz.clinicv2.course.CourseVisitUsage;
import com.sabyeguyz.clinicv2.enums.CancellationType;
import com.sabyeguyz.clinicv2.role.Permission;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancellations (Clinic ERP Workflow Brief Section 6): nothing is deleted, each
 * void or refund is a Transaction_cancellation row naming the permission that
 * allowed it, a reason, and exactly one target.
 *
 * <p>Voiding a sale line also voids the receipt's payments, but only when no
 * active line is left on the receipt, because payments belong to the whole
 * receipt; what to do about a partially voided multi-line receipt that was
 * already paid is a business rule still to be confirmed.</p>
 */
@org.springframework.stereotype.Service
public class TransactionCancellationService {
    private final TransactionCancellationRepository cancellations;
    private final SalesTransactionRepository transactions;
    private final SalesTransactionService salesTransactionService;
    private final PaymentTransactionRepository payments;
    private final PaymentTransactionService paymentTransactionService;
    private final ReceiptService receiptService;

    public TransactionCancellationService(TransactionCancellationRepository cancellations,
                                           SalesTransactionRepository transactions,
                                           SalesTransactionService salesTransactionService,
                                           PaymentTransactionRepository payments,
                                           PaymentTransactionService paymentTransactionService,
                                           ReceiptService receiptService) {
        this.cancellations = cancellations;
        this.transactions = transactions;
        this.salesTransactionService = salesTransactionService;
        this.payments = payments;
        this.paymentTransactionService = paymentTransactionService;
        this.receiptService = receiptService;
    }

    @Transactional
    public TransactionCancellation voidSale(SalesTransaction transaction, Permission permission, String reason,
                                             Long actorId) {
        requireReasonAndPermission(permission, reason);
        require(transaction != null, "ต้องระบุรายการขาย");
        salesTransactionService.voidTransaction(transaction.getId(), actorId);

        Receipt receipt = transaction.getReceipt();
        List<SalesTransaction> lines = transactions.findByReceiptId(receipt.getId());
        boolean receiptFullyVoided = lines.stream().noneMatch(SalesTransaction::isTransactionStatus);
        if (receiptFullyVoided) {
            for (PaymentTransaction payment : payments.findByReceiptIdOrderByIdAsc(receipt.getId())) {
                if (payment.isPaymentStatus()) {
                    voidPayment(payment, permission, reason, actorId);
                }
            }
            receiptService.markVoided(receipt, actorId);
        }
        TransactionCancellation cancellation = newCancellation(permission, reason, CancellationType.Void, actorId);
        cancellation.setTransaction(transaction);
        return cancellations.save(cancellation);
    }

    @Transactional
    public TransactionCancellation voidPayment(PaymentTransaction payment, Permission permission, String reason,
                                                Long actorId) {
        requireReasonAndPermission(permission, reason);
        require(payment != null, "ต้องระบุรายการชำระเงิน");
        paymentTransactionService.voidPayment(payment.getId(), actorId);
        TransactionCancellation cancellation = newCancellation(permission, reason, CancellationType.Void, actorId);
        cancellation.setPayment(payment);
        return cancellations.save(cancellation);
    }

    /** The cancellation a refund hangs off; the money movement itself is a RefundTransaction. */
    @Transactional
    public TransactionCancellation recordRefundCancellation(PaymentTransaction payment, Permission permission,
                                                             String reason, Long actorId) {
        requireReasonAndPermission(permission, reason);
        require(payment != null, "ต้องระบุรายการชำระเงิน");
        TransactionCancellation cancellation = newCancellation(permission, reason, CancellationType.Refund, actorId);
        cancellation.setPayment(payment);
        return cancellations.save(cancellation);
    }

    @Transactional
    public TransactionCancellation recordUsageReversal(CourseVisitUsage usage, Permission permission, String reason,
                                                        Long actorId) {
        requireReasonAndPermission(permission, reason);
        require(usage != null, "ต้องระบุรายการใช้สิทธิ์คอร์ส");
        TransactionCancellation cancellation = newCancellation(permission, reason, CancellationType.UsageReversal, actorId);
        cancellation.setVisitUsage(usage);
        return cancellations.save(cancellation);
    }

    @Transactional
    public TransactionCancellation recordTransferReversal(CourseTransfer transfer, Permission permission,
                                                           String reason, Long actorId) {
        requireReasonAndPermission(permission, reason);
        require(transfer != null, "ต้องระบุรายการโอน");
        TransactionCancellation cancellation = newCancellation(permission, reason, CancellationType.TransferReversal, actorId);
        cancellation.setTransfer(transfer);
        return cancellations.save(cancellation);
    }

    private static TransactionCancellation newCancellation(Permission permission, String reason,
                                                            CancellationType type, Long actorId) {
        TransactionCancellation cancellation = new TransactionCancellation();
        cancellation.setPermission(permission);
        cancellation.setReason(reason.trim());
        cancellation.setCancellationType(type);
        cancellation.setCancelledBy(actorId);
        cancellation.setCancelledAt(Instant.now());
        return cancellation;
    }

    private static void requireReasonAndPermission(Permission permission, String reason) {
        require(permission != null, "ต้องระบุสิทธิ์ที่อนุญาตให้ยกเลิก");
        require(reason != null && !reason.isBlank(), "ต้องระบุเหตุผลที่ยกเลิก");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
