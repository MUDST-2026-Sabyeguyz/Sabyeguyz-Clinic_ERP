package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.audit.AuditLogService;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.enums.ReasonCode;
import com.sabyeguyz.clinicv2.enums.ReceiptStatus;
import com.sabyeguyz.clinicv2.role.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.sabyeguyz.clinicv2.audit.AuditLogService.fields;

@Service
public class CoursePaymentVoidService {

    private final ReceiptRepository receipts;
    private final ReceiptCoursePaymentRepository coursePayments;
    private final SalesTransactionRepository sales;
    private final PaymentTransactionRepository payments;
    private final RefundTransactionRepository refunds;
    private final TransactionCancellationService cancellations;
    private final ReceiptService receiptService;
    private final BranchAccessPolicy branchAccess;
    private final PermissionService permissions;
    private final CurrentActor actor;
    private final AuditLogService audit;
    private final PaymentLockService paymentLocks;

    public CoursePaymentVoidService(
            ReceiptRepository receipts,
            ReceiptCoursePaymentRepository coursePayments,
            SalesTransactionRepository sales,
            PaymentTransactionRepository payments,
            RefundTransactionRepository refunds,
            TransactionCancellationService cancellations,
            ReceiptService receiptService,
            BranchAccessPolicy branchAccess,
            PermissionService permissions,
            CurrentActor actor,
            AuditLogService audit,
            PaymentLockService paymentLocks
    ) {
        this.receipts = receipts;
        this.coursePayments = coursePayments;
        this.sales = sales;
        this.payments = payments;
        this.refunds = refunds;
        this.cancellations = cancellations;
        this.receiptService = receiptService;
        this.branchAccess = branchAccess;
        this.permissions = permissions;
        this.actor = actor;
        this.audit = audit;
        this.paymentLocks = paymentLocks;
    }

    public record Result(
            Long receiptId,
            Long paymentId,
            Long cancellationId
    ) {}

    @Transactional
    public Result voidReceipt(
            Long receiptId,
            ReasonCode reasonCode,
            String reason
    ) {
        Long actorId = actor.requireId();

        if (receiptId == null) {
            throw new IllegalArgumentException("ต้องระบุรหัสใบเสร็จ");
        }

        if (reasonCode == null) {
            throw new IllegalArgumentException("ต้องเลือกประเภทเหตุผล");
        }

        if (reason == null || reason.isBlank()
                || reason.trim().length() > 255) {
            throw new IllegalArgumentException(
                    "ต้องระบุเหตุผลความยาวไม่เกิน 255 ตัวอักษร"
            );
        }

        Receipt receipt = receipts.findByIdForUpdate(receiptId)
                .orElseThrow(() ->
                        new IllegalArgumentException("ไม่พบใบเสร็จ"));

        branchAccess.requireAccess(receipt.getBranch());

        var permission = permissions.requireAllowed(
                "SALE_CANCEL",
                actorId
        );

        if (receipt.getReceiptStatus() != ReceiptStatus.Issued) {
            throw new IllegalArgumentException(
                    "ยกเลิกได้เฉพาะใบเสร็จสถานะ Issued"
            );
        }

        if (!coursePayments.existsByReceipt_Id(receiptId)) {
            throw new IllegalArgumentException(
                    "ใบเสร็จนี้ไม่มีรายการรับเงินคอร์สเดิม"
            );
        }

        // Mixed receipts require a separate cancellation flow.
        if (sales.countByReceiptId(receiptId) > 0) {
            throw new IllegalArgumentException(
                    "method นี้รองรับเฉพาะใบรับเงินคอร์สเดิมที่ไม่มีรายการขาย"
            );
        }

        PaymentTransaction payment = payments
                .findByReceiptIdAndPaymentStatusTrue(receiptId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ไม่พบรายการรับเงินที่ยังมีผล"
                ));

        // Use the same lock order as refund requests.
        payment = paymentLocks.lockForUpdate(payment.getId());

        if (!payment.isPaymentStatus()) {
            throw new IllegalArgumentException(
                    "รายการรับเงินนี้ถูกยกเลิกแล้ว"
            );
        }

        // Do not handle payments with active refunds through this flow.
        if (refunds.refundedOrPendingAmountOf(payment.getId()).signum() > 0) {
            throw new IllegalArgumentException(
                    "รายการนี้มีคำขอคืนเงินที่ยังมีผล ต้องจัดการผ่าน flow คืนเงิน"
            );
        }

        TransactionCancellation cancellation = cancellations.voidPayment(
                payment,
                permission,
                reason.trim(),
                actorId
        );
        cancellation.setReasonCode(reasonCode);

        receiptService.markVoided(receipt, actorId);

        audit.recordChange(
                "payment_transaction",
                String.valueOf(payment.getId()),
                AuditAction.UPDATE,
                fields("payment_status", "Completed"),
                fields(
                        "payment_status", "Voided",
                        "receipt_id", receiptId,
                        "cancellation_id", cancellation.getId()
                ),
                actorId
        );

        audit.recordChange(
                "receipt",
                String.valueOf(receiptId),
                AuditAction.UPDATE,
                fields("receipt_status", "Issued"),
                fields(
                        "receipt_status", "Voided",
                        "cancellation_id", cancellation.getId()
                ),
                actorId
        );

        audit.recordChange(
                "transaction_cancellation",
                String.valueOf(cancellation.getId()),
                AuditAction.INSERT,
                null,
                fields(
                        "payment_id", payment.getId(),
                        "permission_id", permission.getId(),
                        "cancellation_type", "Void",
                        "reason_code", reasonCode.name(),
                        "reason", reason.trim()
                ),
                actorId
        );

        return new Result(
                receiptId,
                payment.getId(),
                cancellation.getId()
        );
    }
}