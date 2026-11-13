package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.course.CourseLedgerRepository;
import com.sabyeguyz.clinicv2.course.CourseVisitUsageRepository;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.ReceiptStatus;
import com.sabyeguyz.clinicv2.enums.UsageStatus;
import com.sabyeguyz.clinicv2.sale.SalesTransactionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AppointmentProgressController {
    public record SaleDocument(
            Long saleId,
            Long receiptId,
            String receiptNo,
            String receiptStatus,
            boolean active) {}

    public record UsageDocument(
            Long usageId,
            Long movementId,
            Long coursePurchaseId,
            String status) {}

    public record Progress(
            Long appointmentId,
            String appointmentStatus,
            String patientType,
            String state,
            String reason,
            Long visitId,
            Long servedPatientId,
            Instant completedAt,
            List<SaleDocument> sales,
            List<UsageDocument> usages) {}

    private final AppointmentVisitResolver resolver;
    private final SalesTransactionRepository sales;
    private final CourseVisitUsageRepository usages;
    private final CourseLedgerRepository ledger;

    public AppointmentProgressController(
            AppointmentVisitResolver resolver,
            SalesTransactionRepository sales,
            CourseVisitUsageRepository usages,
            CourseLedgerRepository ledger) {
        this.resolver = resolver;
        this.sales = sales;
        this.usages = usages;
        this.ledger = ledger;
    }

    @GetMapping("/{id}/progress")
    public Progress get(@PathVariable Long id) {
        var resolved = resolver.resolve(id);
        Appointment appointment = resolved.appointment();
        PatientVisit visit = resolved.visit();

        if (visit == null) {
            return response(
                    appointment, resolved.state(), resolved.reason(),
                    null, List.of(), List.of());
        }

        List<SaleDocument> saleDocuments = new ArrayList<>();
        List<UsageDocument> usageDocuments = new ArrayList<>();
        boolean requiresReview = false;

        for (var sale : sales.findByVisitId(visit.getId())) {
            var receipt = sale.getReceipt();
            if (receipt == null
                    || !visit.getBranch().getId().equals(sale.getBranch().getId())
                    || !visit.getPatient().getId().equals(sale.getPatient().getId())
                    || !visit.getBranch().getId().equals(receipt.getBranch().getId())
                    || !visit.getPatient().getId().equals(receipt.getPatient().getId())) {
                return review(appointment, "ข้อมูลขายหรือใบเสร็จไม่ตรงกับ Visit");
            }

            saleDocuments.add(new SaleDocument(
                    sale.getId(), receipt.getId(), receipt.getReceiptNo(),
                    receipt.getReceiptStatus().name(),
                    sale.isTransactionStatus()));

            if (!sale.isTransactionStatus()
                    || receipt.getReceiptStatus() != ReceiptStatus.Issued) {
                requiresReview = true;
            }
        }

        for (var usage : usages.findByVisitIdOrderByIdAsc(visit.getId())) {
            if (!visit.getPatient().getId().equals(usage.getPatient().getId())
                    || !visit.getBranch().getId()
                    .equals(usage.getCourse().getBranch().getId())) {
                return review(appointment, "ข้อมูลใช้สิทธิ์ไม่ตรงกับ Visit");
            }

            var original = ledger.findByVisitUsageId(usage.getId()).stream()
                    .filter(row ->
                            row.getEntryType() == CourseLedgerEntryType.Treatment)
                    .toList();

            if (original.size() != 1
                    || !usage.getCourse().getId()
                    .equals(original.get(0).getCourse().getId())
                    || !visit.getPatient().getId()
                    .equals(original.get(0).getPatient().getId())) {
                return review(appointment, "ไม่พบรายการตัดสิทธิ์ต้นทางที่ตรงกัน");
            }

            usageDocuments.add(new UsageDocument(
                    usage.getId(), original.get(0).getId(),
                    usage.getCourse().getId(),
                    usage.getUsageStatus().name()));

            if (usage.getUsageStatus() == UsageStatus.Reversed) {
                requiresReview = true;
            }
        }

        if (saleDocuments.isEmpty() && usageDocuments.isEmpty()) {
            return review(appointment, "พบ Visit แต่ไม่พบรายการขายหรือใช้สิทธิ์");
        }

        saleDocuments.sort(
                java.util.Comparator.comparing(SaleDocument::saleId));

        return response(
                appointment,
                requiresReview ? "REVIEW_REQUIRED" : "FOUND",
                requiresReview ? "พบเอกสารที่ย้อนรายการ ต้องตรวจรายละเอียดเดิม" : null,
                visit,
                List.copyOf(saleDocuments),
                List.copyOf(usageDocuments));
    }

    private static Progress review(Appointment appointment, String reason) {
        return response(
                appointment, "REVIEW_REQUIRED", reason,
                null, List.of(), List.of());
    }

    private static Progress response(
            Appointment appointment,
            String state,
            String reason,
            PatientVisit visit,
            List<SaleDocument> sales,
            List<UsageDocument> usages) {
        return new Progress(
                appointment.getId(),
                appointment.getAppointmentStatus().name().toUpperCase(Locale.ROOT),
                appointment.getAppointmentPatientType().name(),
                state,
                reason,
                visit == null ? null : visit.getId(),
                visit == null ? null : visit.getPatient().getId(),
                visit == null ? null : visit.getVisitDate(),
                sales,
                usages);
    }
}