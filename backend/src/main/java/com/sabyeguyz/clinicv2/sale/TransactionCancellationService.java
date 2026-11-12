package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.commission.SingleVisitFeeService;
import com.sabyeguyz.clinicv2.course.CourseRightsService;
import com.sabyeguyz.clinicv2.course.CourseTransfer;
import com.sabyeguyz.clinicv2.course.CourseVisitUsage;
import com.sabyeguyz.clinicv2.enums.CancellationType;
import com.sabyeguyz.clinicv2.enums.TransactionType;
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
    private final CourseRepository courses;
    private final CourseRightsService rights;
    private final SingleVisitFeeService singleVisitFees;

    public TransactionCancellationService(TransactionCancellationRepository cancellations,
                                           SalesTransactionRepository transactions,
                                           SalesTransactionService salesTransactionService,
                                           PaymentTransactionRepository payments,
                                           PaymentTransactionService paymentTransactionService,
                                           ReceiptService receiptService, CourseRepository courses,
                                           CourseRightsService rights, SingleVisitFeeService singleVisitFees) {
        this.courses = courses;
        this.rights = rights;
        this.singleVisitFees = singleVisitFees;
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
        return voidSale(transaction, permission, com.sabyeguyz.clinicv2.enums.ReasonCode.Other, reason, actorId);
    }

    @Transactional
    public TransactionCancellation voidSale(SalesTransaction transaction, Permission permission,
                                             com.sabyeguyz.clinicv2.enums.ReasonCode reasonCode, String reason,
                                             Long actorId) {
        requireReasonAndPermission(permission, reason);
        require(reasonCode != null, "ต้องระบุประเภทเหตุผลที่ยกเลิก");
        require(transaction != null, "ต้องระบุรายการขาย");
        salesTransactionService.voidTransaction(transaction.getId(), actorId);

        Receipt receipt = transaction.getReceipt();
        List<SalesTransaction> lines = transactions.findByReceiptId(receipt.getId());
        boolean receiptFullyVoided = lines.stream().noneMatch(SalesTransaction::isTransactionStatus);
        if (receiptFullyVoided) {
            for (PaymentTransaction payment : payments.findByReceiptIdOrderByIdAsc(receipt.getId())) {
                if (payment.isPaymentStatus()) {
                    voidPayment(payment, permission, reasonCode, reason, actorId);
                }
            }
            receiptService.markVoided(receipt, actorId);
        }
        TransactionCancellation cancellation = newCancellation(permission, reasonCode, reason, CancellationType.Void, actorId);
        cancellation.setTransaction(transaction);
        TransactionCancellation saved = cancellations.save(cancellation);
        // A void stops the rights that were not used and takes back a single-visit fee not yet paid out; the treatment
        // fees and commissions of sessions already given are kept.
        if (transaction.getTransactionType() == TransactionType.CoursePurchase && courses != null) {
            courses.findBySaleTransactionId(transaction.getId())
                    .ifPresent(course -> rights.cutAllForCancellation(course.getId(), saved, reason.trim(), actorId));
        }
        if (transaction.getTransactionType() == TransactionType.SingleVisit && transaction.getVisit() != null
                && singleVisitFees != null) {
            singleVisitFees.reverse(transaction.getVisit(), reason, actorId);
        }
        return saved;
    }

    @Transactional
    public TransactionCancellation voidPayment(PaymentTransaction payment, Permission permission, String reason,
                                                Long actorId) {
        return voidPayment(payment, permission, com.sabyeguyz.clinicv2.enums.ReasonCode.Other, reason, actorId);
    }

    @Transactional
    public TransactionCancellation voidPayment(PaymentTransaction payment, Permission permission,
                                                com.sabyeguyz.clinicv2.enums.ReasonCode reasonCode, String reason,
                                                Long actorId) {
        requireReasonAndPermission(permission, reason);
        require(reasonCode != null, "ต้องระบุประเภทเหตุผลที่ยกเลิก");
        require(payment != null, "ต้องระบุรายการชำระเงิน");
        paymentTransactionService.voidPayment(payment.getId(), actorId);
        TransactionCancellation cancellation = newCancellation(permission, reasonCode, reason, CancellationType.Void, actorId);
        cancellation.setPayment(payment);
        return cancellations.save(cancellation);
    }

    /** The cancellation a refund hangs off; the money movement itself is a RefundTransaction. */
    @Transactional
    public TransactionCancellation recordRefundCancellation(PaymentTransaction payment, Permission permission,
                                                             String reason, Long actorId) {
        return recordRefundCancellation(payment, permission, com.sabyeguyz.clinicv2.enums.ReasonCode.Other, reason, actorId);
    }

    @Transactional
    public TransactionCancellation recordRefundCancellation(PaymentTransaction payment, Permission permission,
                                                             com.sabyeguyz.clinicv2.enums.ReasonCode reasonCode,
                                                             String reason, Long actorId) {
        requireReasonAndPermission(permission, reason);
        require(reasonCode != null, "ต้องระบุประเภทเหตุผลที่ยกเลิก");
        require(payment != null, "ต้องระบุรายการชำระเงิน");
        TransactionCancellation cancellation = newCancellation(permission, reasonCode, reason, CancellationType.Refund, actorId);
        cancellation.setPayment(payment);
        return cancellations.save(cancellation);
    }

    @Transactional
    public TransactionCancellation recordUsageReversal(CourseVisitUsage usage, Permission permission, String reason,
                                                        Long actorId) {
        return recordUsageReversal(usage, permission, com.sabyeguyz.clinicv2.enums.ReasonCode.Other, reason, actorId);
    }

    @Transactional
    public TransactionCancellation recordUsageReversal(CourseVisitUsage usage, Permission permission,
                                                        com.sabyeguyz.clinicv2.enums.ReasonCode reasonCode,
                                                        String reason, Long actorId) {
        requireReasonAndPermission(permission, reason);
        require(reasonCode != null, "ต้องระบุประเภทเหตุผลที่ยกเลิก");
        require(usage != null, "ต้องระบุรายการใช้สิทธิ์คอร์ส");
        TransactionCancellation cancellation = newCancellation(permission, reasonCode, reason, CancellationType.UsageReversal, actorId);
        cancellation.setVisitUsage(usage);
        return cancellations.save(cancellation);
    }

    @Transactional
    public TransactionCancellation recordTransferReversal(CourseTransfer transfer, Permission permission,
                                                           String reason, Long actorId) {
        return recordTransferReversal(transfer, permission, com.sabyeguyz.clinicv2.enums.ReasonCode.Other, reason, actorId);
    }

    @Transactional
    public TransactionCancellation recordTransferReversal(CourseTransfer transfer, Permission permission,
                                                           com.sabyeguyz.clinicv2.enums.ReasonCode reasonCode,
                                                           String reason, Long actorId) {
        requireReasonAndPermission(permission, reason);
        require(reasonCode != null, "ต้องระบุประเภทเหตุผลที่ยกเลิก");
        require(transfer != null, "ต้องระบุรายการโอน");
        TransactionCancellation cancellation = newCancellation(permission, reasonCode, reason, CancellationType.TransferReversal, actorId);
        cancellation.setTransfer(transfer);
        return cancellations.save(cancellation);
    }

    private static TransactionCancellation newCancellation(Permission permission,
                                                           com.sabyeguyz.clinicv2.enums.ReasonCode reasonCode, String reason,
                                                            CancellationType type, Long actorId) {
        TransactionCancellation cancellation = new TransactionCancellation();
        cancellation.setPermission(permission);
        cancellation.setReason(reason.trim());
        cancellation.setReasonCode(reasonCode);
        cancellation.setCancellationType(type);
        cancellation.setCancelledBy(actorId);
        cancellation.setCancelledAt(Instant.now());
        return cancellation;
    }

    private static void requireReasonAndPermission(Permission permission, String reason) {
        require(permission != null, "ต้องระบุสิทธิ์ที่อนุญาตให้ยกเลิก");
        require(reason != null && !reason.isBlank(), "ต้องระบุเหตุผลที่ยกเลิก");
        require(reason.trim().length() <= 255, "เหตุผลที่ยกเลิกต้องไม่เกิน 255 ตัวอักษร");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
