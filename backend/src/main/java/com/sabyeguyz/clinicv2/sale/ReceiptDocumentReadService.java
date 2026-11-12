package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.patient.Patient;

/** Receipt-based HTTP views; legacy sales IDs and response shapes remain independent. */
@Service
@Transactional(readOnly = true)
public class ReceiptDocumentReadService {
    private final ReceiptReadService reader;
    private final ReceiptRepository receipts;
    private final PaymentTransactionRepository payments;

    public ReceiptDocumentReadService(
            ReceiptReadService reader,
            ReceiptRepository receipts,
            PaymentTransactionRepository payments) {
        this.reader = reader;
        this.receipts = receipts;
        this.payments = payments;
    }

    public record Buyer(
            Long id,
            String hn,
            String fullName) {}

    public record BranchHeader(
            Long id,
            String code,
            String name,
            String address,
            String phone,
            String email,
            String licenseNo) {}

    public record Document(
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
            List<ReceiptReadService.SaleLine> sales,
            List<ReceiptReadService.CoursePaymentLine> coursePayments,
            List<ReceiptReadService.PaymentLine> payments,
            Buyer patient,
            BranchHeader branch) {}

    public record Summary(
            Long receiptId,
            String receiptNo,
            Long patientId,
            Long branchId,
            LocalDateTime saleDate,
            String receiptStatus,
            String paymentLabelCode,
            String paymentLabelName,
            BigDecimal netAmount,
            Buyer patient,
            BranchHeader branch,
            List<ReceiptReadService.PaymentLine> payments) {}

    public record PageResponse(
            List<Summary> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {}

    public Document getDetail(Long receiptId) {
        requireReceipt(receiptId);

        return document(reader.getDetail(receiptId));
    }

    public PageResponse search(
            Long patientId,
            String branchCode,
            int page,
            int size) {
        var result = reader.searchReceiptHistory(
                patientId,
                branchCode,
                page,
                size);
        // Search has already resolved branch scope. Preserve its existing all-branch Manager behavior.
        var items = result.getContent()
                .stream()
                .map(summary -> {
            Receipt receipt = requireReceipt(summary.receiptId());

            return new Summary(
                    summary.receiptId(),
                    summary.receiptNo(),
                    summary.patientId(),
                    summary.branchId(),
                    summary.saleDate(),
                    summary.receiptStatus(),
                    summary.paymentLabelCode(),
                    summary.paymentLabelName(),
                    summary.netAmount(),
                    buyer(receipt.getPatient()),
                    branch(receipt.getBranch()),
                    paymentLines(receipt.getId()));
        }).toList();

        return new PageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public List<Document> getCourseHistory(Long courseId) {
        // The reader checks every receipt before returning; inaccessible history fails as one request.

        return reader.getCourseHistory(courseId)
                .stream()
                .map(this::document)
                .toList();
    }

    private Document document(ReceiptReadService.Detail detail) {
        Receipt receipt = requireReceipt(detail.receiptId());

        return new Document(
                detail.receiptId(),
                detail.receiptNo(),
                detail.patientId(),
                detail.branchId(),
                detail.saleDate(),
                detail.receiptStatus(),
                detail.paymentLabelCode(),
                detail.paymentLabelName(),
                detail.subtotalBeforeDiscount(),
                detail.billDiscountAmount(),
                detail.netAmount(),
                detail.sales(),
                detail.coursePayments(),
                detail.payments(),
                buyer(receipt.getPatient()),
                branch(receipt.getBranch()));
    }

    private Receipt requireReceipt(Long receiptId) {
        if (receiptId == null || receiptId <= 0) {
            throw new IllegalArgumentException("ต้องระบุรหัสใบเสร็จที่ถูกต้อง");
        }

        return receipts.findById(receiptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบใบเสร็จ"));
    }

    private List<ReceiptReadService.PaymentLine> paymentLines(Long receiptId) {
        return payments.findByReceiptIdOrderByIdAsc(receiptId).stream()
                .map(payment -> new ReceiptReadService.PaymentLine(
                        payment.getId(),
                        payment.getPaymentDate(),
                        payment.getPaymentMethod().getMethodName(),
                        payment.getAmount(),
                        payment.isPaymentStatus() ? "Completed" : "Voided"))
                .toList();
    }

    private static Buyer buyer(Patient patient) {
        String name = Stream.of(
                        patient.getPatientFname(),
                        patient.getPatientMiddleName(),
                        patient.getPatientLname())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));

        return new Buyer(
                patient.getId(),
                patient.getPatientHn(),
                name);
    }

    private static BranchHeader branch(Branch branch) {
        return new BranchHeader(
                branch.getId(),
                branch.getBranchCode(),
                branch.getBranchName(),
                branch.getAddress(),
                branch.getPhone(),
                branch.getBranchEmail(),
                branch.getLicenseNo());
    }
}
