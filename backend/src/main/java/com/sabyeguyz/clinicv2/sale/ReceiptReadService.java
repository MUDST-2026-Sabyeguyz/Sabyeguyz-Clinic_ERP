package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;

@Service
@Transactional(readOnly = true)
public class ReceiptReadService {
    private final ReceiptRepository receipts;
    private final SalesTransactionRepository sales;
    private final ReceiptCoursePaymentRepository coursePayments;
    private final PaymentTransactionRepository payments;
    private final BranchAccessPolicy branchAccess;

    public ReceiptReadService(
            ReceiptRepository receipts,
            SalesTransactionRepository sales,
            ReceiptCoursePaymentRepository coursePayments,
            PaymentTransactionRepository payments,
            BranchAccessPolicy branchAccess) {
        this.receipts = receipts;
        this.sales = sales;
        this.coursePayments = coursePayments;
        this.payments = payments;
        this.branchAccess = branchAccess;
    }

    public record SaleLine(
            Long transactionId,
            int lineNo,
            String transactionType,
            String itemNameSnapshot,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            BigDecimal lineDiscountAmount,
            BigDecimal allocatedBillDiscountAmount,
            boolean effective) {}

    public record CoursePaymentLine(
            Long courseId,
            String itemNameSnapshot,
            BigDecimal amount,
            BigDecimal allocatedBillDiscountAmount,
            BigDecimal netAmount) {}

    public record PaymentLine(
            Long paymentId,
            Instant paymentDate,
            String paymentMethod,
            BigDecimal amount,
            String status) {}

    public record Detail(
            Long receiptId,
            String receiptNo,
            Long patientId,
            Long branchId,
            LocalDateTime saleDate,
            String receiptStatus,
            String paymentLabelCode,
            String paymentLabelName,
            BigDecimal subtotalBeforeDiscount,
            BigDecimal billDiscountAmount,
            BigDecimal netAmount,
            List<SaleLine> sales,
            List<CoursePaymentLine> coursePayments,
            List<PaymentLine> payments) {}

    public record Summary(
            Long receiptId,
            String receiptNo,
            Long patientId,
            Long branchId,
            LocalDateTime saleDate,
            String receiptStatus,
            String paymentLabelCode,
            String paymentLabelName,
            BigDecimal netAmount) {}

    public Detail getDetail(Long receiptId) {
        if (receiptId == null) {
            throw new IllegalArgumentException("ต้องระบุรหัสใบเสร็จ");
        }

        Receipt receipt = receipts.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบใบเสร็จ"));

        branchAccess.requireAccess(receipt.getBranch());

        // Read each collection separately to avoid multiplying detail rows.
        List<SaleLine> saleLines = sales.findReceiptLines(receiptId)
                .stream()
                .map(line -> new SaleLine(
                        line.getId(),
                        line.getLineNo(),
                        line.getTransactionType().name(),
                        line.getItemNameSnapshot(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getAmount(),
                        line.getLineDiscountAmount(),
                        line.getAllocatedBillDiscountAmount(),
                        line.isTransactionStatus()))
                .toList();

        List<CoursePaymentLine> courseLines = coursePayments
                .findByReceipt_IdOrderByCourse_IdAsc(receiptId)
                .stream()
                .map(item -> new CoursePaymentLine(
                        item.getCourse().getId(),
                        item.getItemNameSnapshot(),
                        item.getAmount(),
                        item.getAllocatedBillDiscountAmount(),
                        item.getAmount().subtract(item.getAllocatedBillDiscountAmount())))
                .toList();

        List<PaymentLine> paymentLines = payments
                .findByReceiptIdOrderByIdAsc(receiptId)
                .stream()
                .map(payment -> new PaymentLine(
                        payment.getId(),
                        payment.getPaymentDate(),
                        payment.getPaymentMethod().getMethodName(),
                        payment.getAmount(),
                        payment.isPaymentStatus()
                                ? "Completed"
                                : "Voided"))
                .toList();

        // Preserve the stored receipt totals instead of recalculating history.

        return new Detail(
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getPatient().getId(),
                receipt.getBranch().getId(),
                receipt.getSaleDate(),
                receipt.getReceiptStatus().name(),
                receipt.getPaymentLabelOption().getCode(),
                receipt.getPaymentLabelOption().getName(),
                receipt.getSubtotalBeforeDiscount(),
                receipt.getBillDiscountAmount(),
                receipt.getNetAmount(),
                saleLines,
                courseLines,
                paymentLines);
    }

    /**
     * Returns receipt details for the original purchase and later collections.
     * Every receipt must pass the existing branch access policy.
     */
    public List<Detail> getCourseHistory(Long courseId) {
        if (courseId == null || courseId <= 0) {
            throw new IllegalArgumentException("ต้องระบุรหัสคอร์สที่ถูกต้อง");
        }

        // Authenticate and resolve branch access even when history is empty.
        branchAccess.scope(null);

        return receipts.findCourseReceiptHistory(courseId)
                .stream()
                .map(receipt -> getDetail(receipt.getId()))
                .toList();
    }

    /**
     * Searches receipt headers after resolving the caller's branch scope.
     * Uses stored document totals, not cumulative course payments.
     */
    public Page<Summary> searchReceiptHistory(
            Long patientId,
            String branchCode,
            int page,
            int size) {
        if (patientId != null && patientId <= 0) {
            throw new IllegalArgumentException("รหัสผู้ป่วยไม่ถูกต้อง");
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "หน้าต้องไม่ติดลบและจำนวนต่อหน้าต้องอยู่ระหว่าง 1–100"
            );
        }

        // Resolve access before querying, including empty search results.
        var scopedBranch = branchAccess.scope(branchCode);
        Long branchId = scopedBranch == null
                ? null
                : scopedBranch.getId();

        return receipts.searchReceiptHistory(
                patientId,
                branchId,
                PageRequest.of(page, size))
                .map(receipt -> new Summary(
                        receipt.getId(),
                        receipt.getReceiptNo(),
                        receipt.getPatient().getId(),
                        receipt.getBranch().getId(),
                        receipt.getSaleDate(),
                        receipt.getReceiptStatus().name(),
                        receipt.getPaymentLabelOption().getCode(),
                        receipt.getPaymentLabelOption().getName(),
                        receipt.getNetAmount()));
    }
}
