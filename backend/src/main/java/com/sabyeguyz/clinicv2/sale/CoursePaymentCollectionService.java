package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.audit.AuditLogService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.sabyeguyz.clinicv2.audit.AuditLogService.fields;

@Service
public class CoursePaymentCollectionService {

    private final ReceiptService receipts;
    private final ReceiptCoursePaymentService coursePayments;
    private final ReceiptCoursePaymentRepository paymentItems;
    private final PaymentTransactionService payments;
    private final BranchAccessPolicy branchAccess;
    private final CurrentActor actor;
    private final AuditLogService audit;

    public CoursePaymentCollectionService(
            ReceiptService receipts, ReceiptCoursePaymentService coursePayments,
            ReceiptCoursePaymentRepository paymentItems, PaymentTransactionService payments,
            BranchAccessPolicy branchAccess, CurrentActor actor, AuditLogService audit
    ) {
        this.receipts = receipts;
        this.coursePayments = coursePayments;
        this.paymentItems = paymentItems;
        this.payments = payments;
        this.branchAccess = branchAccess;
        this.actor = actor;
        this.audit = audit;
    }

    public record Result(
            Long receiptId,
            Long paymentId,
            String receiptNo,
            BigDecimal netAmount
    ) {}

    @Transactional
    public Result collect(
            Patient patient, Branch branch,
            List<ReceiptCoursePaymentService.ItemRequest> items,
            Discount billDiscount, String paymentLabelCode,
            PaymentMethod paymentMethod, BigDecimal paymentAmount
    ) {
        Long actorId = actor.requireId();

        if (patient == null || patient.getId() == null) {
            throw new IllegalArgumentException("ต้องระบุผู้ป่วยที่มีอยู่แล้ว");
        }

        if (branch == null || branch.getId() == null) {
            throw new IllegalArgumentException("ต้องระบุสาขาที่มีอยู่แล้ว");
        }

        branchAccess.requireAccess(branch);

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("ต้องมีรายการรับเงินคอร์สเดิมอย่างน้อย 1 รายการ"
            );
        }

        String receiptNo = receipts.nextReceiptNo(
                branch, ClinicTime.today()
        );

        Receipt receipt = receipts.create(
                receiptNo, patient, branch, ClinicTime.today(), paymentLabelCode, actorId
        );

        List<ReceiptCoursePayment> prepared =
                coursePayments.prepareAll(receipt, items);

        // Allocate discounts before persisting the payment items.
        receipt = receipts.finalizeTotals(
                receipt, List.of(), prepared, billDiscount, actorId
        );

        paymentItems.saveAllAndFlush(prepared);

        // A payment failure rolls back the receipt and its items together.
        PaymentTransaction payment = payments.record(
                patient, branch, receipt, paymentAmount, paymentMethod, actorId
        );

        // Record the receipt and its collected payment in the same transaction.
        audit.recordChange(
                "receipt",
                String.valueOf(receipt.getId()),
                AuditAction.INSERT,
                null,
                fields(
                        "receipt_no", receipt.getReceiptNo(),
                        "patient_id", patient.getId(),
                        "branch_id", branch.getId(),
                        "payment_label_id",
                        receipt.getPaymentLabelOption().getId(),
                        "subtotal_before_discount",
                        receipt.getSubtotalBeforeDiscount(),
                        "bill_discount_amount", receipt.getBillDiscountAmount(),
                        "net_amount", receipt.getNetAmount()
                ),
                actorId
        );

        for (ReceiptCoursePayment item : prepared) {
            audit.recordChange(
                    "receipt_course_payment",
                    receipt.getId() + ":" + item.getCourse().getId(),
                    AuditAction.INSERT,
                    null,
                    fields(
                            "receipt_id", receipt.getId(),
                            "course_id", item.getCourse().getId(),
                            "item_name_snapshot", item.getItemNameSnapshot(),
                            "amount", item.getAmount(),
                            "allocated_bill_discount_amount",
                            item.getAllocatedBillDiscountAmount()
                    ),
                    actorId
            );
        }

        audit.recordChange(
                "payment_transaction",
                String.valueOf(payment.getId()),
                AuditAction.INSERT,
                null,
                fields(
                        "receipt_id", receipt.getId(),
                        "patient_id", patient.getId(),
                        "branch_id", branch.getId(),
                        "amount", payment.getAmount()
                ),
                actorId
        );

        return new Result(receipt.getId(), payment.getId(), receipt.getReceiptNo(), receipt.getNetAmount()
        );
    }
}