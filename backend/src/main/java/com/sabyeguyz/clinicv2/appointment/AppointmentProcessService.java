package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.course.CourseVisitUsageRepository;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.enums.ReceiptStatus;
import com.sabyeguyz.clinicv2.enums.UsageStatus;
import com.sabyeguyz.clinicv2.sale.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AppointmentProcessService {
    private final AppointmentRepository appointments;
    private final AppointmentProcessRepository processes;
    private final BranchAccessPolicy access;
    private final ReceiptRepository receipts;
    private final PaymentTransactionRepository payments;
    private final SalesTransactionRepository sales;
    private final CourseVisitUsageRepository usages;
    private final PaymentTransactionService collection;
    private final PaymentMethodRepository methods;
    private final com.sabyeguyz.clinicv2.auth.CurrentActor actor;
    private final SingleVisitFeeAccrual fees;
    private final RefundTransactionRepository refunds;
    private final com.sabyeguyz.clinicv2.audit.AuditLogService audit;
    private final PatientVisitRepository visits;
    public AppointmentProcessService(AppointmentRepository appointments, AppointmentProcessRepository processes,
            BranchAccessPolicy access, ReceiptRepository receipts, PaymentTransactionRepository payments,
            SalesTransactionRepository sales, CourseVisitUsageRepository usages, PaymentTransactionService collection,
            PaymentMethodRepository methods, com.sabyeguyz.clinicv2.auth.CurrentActor actor, SingleVisitFeeAccrual fees,
            RefundTransactionRepository refunds, com.sabyeguyz.clinicv2.audit.AuditLogService audit, PatientVisitRepository visits) {
        this.appointments = appointments; this.processes = processes; this.access = access;
        this.receipts = receipts; this.payments = payments; this.sales = sales; this.usages = usages;
        this.collection = collection; this.methods = methods; this.actor = actor; this.fees = fees;
        this.refunds = refunds;
        this.audit = audit;
        this.visits = visits;
    }
    public record Progress(Long appointmentId, boolean registered, Long patientId, String paymentStatus,
            Long saleId, String receiptNo, BigDecimal paid, BigDecimal outstanding,
            String courseStatus, Long courseId, boolean includesCourse, boolean canManage, boolean canReview) {}

    @Transactional(readOnly = true)
    public Progress progress(Long id) {
        Appointment appointment = appointments.findById(id).orElseThrow(AppointmentNotFoundException::new);
        access.requireAccess(appointment.getBranch());
        AppointmentProcess process = processes.findById(id).orElse(null);
        String paymentStatus = "NOT_STARTED", courseStatus = "NOT_STARTED", receiptNo = null;
        Long saleId = null, courseId = null;
        boolean includesCourse = false;
        BigDecimal paid = BigDecimal.ZERO, outstanding = BigDecimal.ZERO;
        if (process != null && process.isRequiresReview()) paymentStatus = "REVIEW_REQUIRED";
        if (process != null && process.getReceiptId() != null) {
            Receipt receipt = receipts.findById(process.getReceiptId()).orElseThrow();
            paid = payments.paidAmountOf(receipt.getId());
            outstanding = receipt.getNetAmount().subtract(paid).max(BigDecimal.ZERO);
            // Pending/completed refunds require review; never relabel returned money as unpaid and collect it again.
            boolean hasRefund = payments.findByReceiptIdOrderByIdAsc(receipt.getId()).stream()
                    .anyMatch(payment -> refunds.refundedOrPendingAmountOf(payment.getId()).signum() > 0);
            paymentStatus = receipt.getReceiptStatus() != ReceiptStatus.Issued || hasRefund ? "REVERSED"
                    : outstanding.signum() == 0 ? "PAID" : paid.signum() > 0 ? "PARTIAL" : "UNPAID";
            receiptNo = receipt.getReceiptNo();
            saleId = sales.findByReceiptId(receipt.getId()).stream().findFirst().map(SalesTransaction::getId).orElse(null);
            includesCourse = sales.findByReceiptId(receipt.getId()).stream().anyMatch(line -> line.getTransactionType()
                    == com.sabyeguyz.clinicv2.enums.TransactionType.CoursePurchase);
        }
        if (process != null && process.getUsageId() != null) {
            var usage = usages.findById(process.getUsageId()).orElseThrow();
            courseStatus = usage.getUsageStatus() == UsageStatus.Completed ? "USED" : "REVERSED";
            courseId = usage.getCourse().getId();
        }
        return new Progress(id, appointment.getPatient() != null && appointment.getPatient().getPatientHn() != null
                    && !appointment.getPatient().getPatientHn().isBlank(),
                appointment.getPatient() == null ? null : appointment.getPatient().getId(), paymentStatus,
                saleId, receiptNo, paid, outstanding, courseStatus, courseId, includesCourse,
                (access.isManager() || appointment.getStaff().getId().equals(access.currentStaffId()))
                    && (appointment.getAppointmentStatus() == AppointmentStatus.Confirmed
                        || appointment.getAppointmentStatus() == AppointmentStatus.Arrived
                        || appointment.getAppointmentStatus() == AppointmentStatus.InService), access.isManager());
    }

    /** Collect only the outstanding balance on the existing bill. The appointment lock
     * makes simultaneous full-balance retries return the paid bill instead of collecting twice. */
    public Progress settle(Long id, BigDecimal expectedAmount, String methodCode) {
        lock(id, null, null);
        AppointmentProcess process = links(id);
        if (process.getReceiptId() == null) throw new IllegalArgumentException("ยังไม่มีใบรายการของนัดหมายนี้");
        Receipt receipt = receipts.findByIdForUpdate(process.getReceiptId()).orElseThrow();
        if (receipt.getReceiptStatus() != ReceiptStatus.Issued) throw new IllegalArgumentException("ใบรายการนี้ถูกย้อนคืนแล้ว");
        if ("REVERSED".equals(progress(id).paymentStatus())) throw new IllegalArgumentException("มีรายการคืนเงิน กรุณาตรวจสอบประวัติก่อนรับชำระ");
        BigDecimal remaining = receipt.getNetAmount().subtract(payments.paidAmountOf(receipt.getId()));
        if (remaining.signum() <= 0) return progress(id);
        if (expectedAmount == null || expectedAmount.compareTo(remaining) != 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ยอดคงเหลือเปลี่ยนแล้ว กรุณาโหลดข้อมูลใหม่");
        var method = methods.findByMethodNameIgnoreCase(PaymentMethod.canonicalName(methodCode))
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบช่องทางชำระเงิน"));
        collection.record(receipt.getPatient(), receipt.getBranch(), receipt, remaining, method, actor.currentId());
        attachExistingVisit(id);
        return progress(id);
    }
    /** Attach prepaid service lines only when a real appointment visit exists.
     * This prevents prepayment from creating a second, completed walk-in in attendance reports. */
    public void attachExistingVisit(Long id) {
        var process = processes.findById(id).orElse(null);
        var visit = visits.findFirstByAppointmentIdOrderByIdDesc(id).orElse(null);
        if (process == null || process.getReceiptId() == null || visit == null) return;
        var lines = sales.findByReceiptId(process.getReceiptId());
        for (var line : lines) {
            if (line.getTransactionType() == com.sabyeguyz.clinicv2.enums.TransactionType.SingleVisit && line.getVisit() == null) {
                line.setVisit(visit);
                line.setTreatingStaff(visit.getTreatingStaff() == null ? visit.getAssignedStaff() : visit.getTreatingStaff());
            }
        }
        sales.saveAll(lines);
        // Earn the treatment fee after actual service completion and full payment, not at booking/payment time.
        if (visit.getVisitStatus() == com.sabyeguyz.clinicv2.enums.VisitStatus.Completed)
            fees.accrue(receipts.findById(process.getReceiptId()).orElseThrow(), lines, actor.currentId());
    }

    /** Lock order is appointment -> course/receipt. All appointment writes share this gate. */
    public Appointment lock(Long id, Long patientId, String branchCode) {
        Appointment appointment = appointments.findByIdForUpdate(id).orElseThrow(AppointmentNotFoundException::new);
        access.requireAccess(appointment.getBranch());
        if (!access.isManager() && !appointment.getStaff().getId().equals(access.currentStaffId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "จัดการได้เฉพาะนัดของตัวเอง");
        if (appointment.getPatient() == null || appointment.getPatient().getPatientHn() == null
                || appointment.getPatient().getPatientHn().isBlank()
                || (patientId != null && !appointment.getPatient().getId().equals(patientId)))
            throw new IllegalArgumentException("ผู้ป่วยต้องลงทะเบียนและตรงกับนัดหมาย");
        if (branchCode != null && !appointment.getBranch().getBranchCode().equals(branchCode))
            throw new IllegalArgumentException("สาขาของรายการต้องตรงกับนัดหมาย");
        return appointment;
    }
    public void requireOpen(Appointment appointment) {
        if (appointment.getAppointmentStatus() != AppointmentStatus.Confirmed
                && appointment.getAppointmentStatus() != AppointmentStatus.Arrived
                && appointment.getAppointmentStatus() != AppointmentStatus.InService)
            throw new IllegalArgumentException("ต้องยืนยันนัดหมายที่ยังไม่สิ้นสุดก่อนทำรายการ");
    }
    public AppointmentProcess links(Long id) { return processes.findById(id).orElseGet(() -> new AppointmentProcess(id)); }
    public void save(AppointmentProcess process) { processes.save(process); }
    public void requireVerified(AppointmentProcess process) {
        if (process.isRequiresReview()) throw new IllegalArgumentException("นัดหมายเก่าต้องให้ผู้จัดการตรวจสอบประวัติก่อนทำรายการใหม่");
    }
    public record DocumentOption(Long id, String label) {}
    public record Candidates(java.util.List<DocumentOption> receipts, java.util.List<DocumentOption> usages) {}
    @Transactional(readOnly = true)
    public Candidates candidates(Long id) {
        access.requireManager();
        Appointment appointment = appointments.findById(id).orElseThrow(AppointmentNotFoundException::new);
        access.requireAccess(appointment.getBranch());
        if (appointment.getPatient() == null) throw new IllegalArgumentException("ต้องลงทะเบียนก่อนตรวจสอบรายการเดิม");
        return new Candidates(
            receipts.findByPatientIdAndBranchIdOrderByIdDesc(appointment.getPatient().getId(), appointment.getBranch().getId()).stream()
                .map(item -> new DocumentOption(item.getId(), item.getReceiptNo() + " · " + item.getSaleDate().toLocalDate() + " · " + item.getNetAmount() + " บาท")).toList(),
            usages.findByPatientIdAndVisitBranchIdOrderByIdDesc(appointment.getPatient().getId(), appointment.getBranch().getId()).stream()
                .map(item -> new DocumentOption(item.getId(), "ใช้สิทธิ์ #" + item.getId() + " · คอร์ส #" + item.getCourse().getId() + " · " + item.getVisitDate().toLocalDate())).toList());
    }
    /** Manager selects the exact historical documents after inspection; never infer them by patient/date. */
    public Progress reconcile(Long id, Long receiptId, Long usageId, String reason) {
        access.requireManager();
        Appointment appointment = lock(id, null, null);
        AppointmentProcess process = links(id);
        if (!process.isRequiresReview()) throw new IllegalArgumentException("นัดหมายนี้ผ่านการตรวจสอบแล้ว ไม่สามารถเปลี่ยนเอกสารที่เชื่อมได้");
        if (reason == null || reason.isBlank() || (receiptId == null && usageId == null))
            throw new IllegalArgumentException("ต้องเลือกเอกสารและระบุผลการตรวจสอบ");
        if (receiptId != null) {
            Receipt receipt = receipts.findByIdForUpdate(receiptId).orElseThrow();
            if (processes.existsByReceiptIdAndAppointmentIdNot(receiptId, id))
                throw new IllegalArgumentException("ใบรายการนี้เชื่อมกับนัดหมายอื่นแล้ว");
            if (!receipt.getPatient().getId().equals(appointment.getPatient().getId())
                    || !receipt.getBranch().getId().equals(appointment.getBranch().getId()))
                throw new IllegalArgumentException("ใบรายการต้องเป็นของผู้ป่วยและสาขาเดียวกับนัดหมาย");
            if (sales.findByReceiptId(receiptId).stream().anyMatch(line -> line.getVisit() != null
                    && line.getVisit().getAppointment() != null && !line.getVisit().getAppointment().getId().equals(id)))
                throw new IllegalArgumentException("ใบรายการเป็นของนัดหมายอื่น");
            process.setReceiptId(receiptId);
        }
        if (usageId != null) {
            var usage = usages.findByIdForUpdate(usageId).orElseThrow();
            if (processes.existsByUsageIdAndAppointmentIdNot(usageId, id))
                throw new IllegalArgumentException("รายการใช้สิทธิ์นี้เชื่อมกับนัดหมายอื่นแล้ว");
            if (!usage.getPatient().getId().equals(appointment.getPatient().getId())
                    || !usage.getVisit().getBranch().getId().equals(appointment.getBranch().getId())
                    || (usage.getVisit().getAppointment() != null && !usage.getVisit().getAppointment().getId().equals(id)))
                throw new IllegalArgumentException("รายการใช้สิทธิ์ไม่ตรงกับนัดหมาย");
            process.setUsageId(usageId);
        }
        process.setRequiresReview(false);
        processes.save(process);
        audit.record("appointment", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                "REVIEW_REQUIRED", "Linked receipt=" + receiptId + ", usage=" + usageId + ": " + reason.trim(), actor.currentId());
        return progress(id);
    }
    /** Explicit, audited acknowledgement only. Never auto-clear this during navigation. */
    public Progress verifyNoPreviousDocuments(Long id, String reason) {
        access.requireManager();
        lock(id, null, null);
        AppointmentProcess process = links(id);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("ต้องระบุผลการตรวจสอบประวัติ");
        if (process.getReceiptId() != null || process.getUsageId() != null)
            throw new IllegalArgumentException("นัดหมายนี้มีรายการเชื่อมแล้ว กรุณาใช้รายการเดิม");
        if (process.isRequiresReview()) {
            process.setRequiresReview(false);
            processes.save(process);
            audit.record("appointment", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                    "REVIEW_REQUIRED", "Verified no previous bill/usage: " + reason.trim(), actor.currentId());
        }
        return progress(id);
    }
    /** A different payload is a new intent, not a retry; never silently replace an existing financial document. */
    public static String fingerprint(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
