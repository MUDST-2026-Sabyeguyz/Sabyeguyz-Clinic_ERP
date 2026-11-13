package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.appointment.Appointment;
import com.sabyeguyz.clinicv2.appointment.AppointmentRepository;
import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.appointment.PatientVisitService;
import com.sabyeguyz.clinicv2.commission.SingleVisitFeeService;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores and redeems prepaid single-visit service rights. */
@Service
public class SingleVisitEntitlementService {
    private final SingleVisitEntitlementRepository entitlements;
    private final SingleVisitEntitlementUsageRepository usages;
    private final AppointmentRepository appointments;
    private final StaffRepository staff;
    private final PatientVisitService visits;
    private final SingleVisitFeeService fees;
    private final com.sabyeguyz.clinicv2.audit.AuditLogService audit;

    public SingleVisitEntitlementService(SingleVisitEntitlementRepository entitlements,
                                         SingleVisitEntitlementUsageRepository usages,
                                         AppointmentRepository appointments, StaffRepository staff,
                                         PatientVisitService visits, SingleVisitFeeService fees,
                                         com.sabyeguyz.clinicv2.audit.AuditLogService audit) {
        this.entitlements = entitlements;
        this.usages = usages;
        this.appointments = appointments;
        this.staff = staff;
        this.visits = visits;
        this.fees = fees;
        this.audit = audit;
    }

    public record Balance(SingleVisitEntitlement entitlement, int used, int remaining) {}

    @Transactional(readOnly = true)
    public List<Balance> listForPatient(Long patientId) {
        return entitlements.findActiveByPatientId(patientId).stream().map(this::balance).toList();
    }

    @Transactional(readOnly = true)
    public Balance balance(SingleVisitEntitlement entitlement) {
        int used = Math.toIntExact(usages.countByEntitlementIdAndReversedAtIsNull(entitlement.getId()));
        return new Balance(entitlement, used, Math.max(0, entitlement.getPurchasedQuantity() - used));
    }

    @Transactional
    public SingleVisitEntitlement recordPurchase(SalesTransaction transaction, Long actorId) {
        require(transaction != null && transaction.getTransactionType() == TransactionType.SingleVisit,
                "สิทธิ์บริการล่วงหน้าบันทึกได้เฉพาะบริการรายครั้ง");
        require(transaction.getVisit() == null, "บริการที่ผูกกับ Visit แล้วไม่ใช่สิทธิ์ล่วงหน้า");
        require(transaction.isTransactionStatus(), "ไม่สามารถสร้างสิทธิ์จากรายการขายที่ยกเลิกแล้ว");
        require(transaction.getQuantity() > 0, "จำนวนสิทธิ์ต้องมากกว่า 0");
        var existing = entitlements.findByTransactionId(transaction.getId());
        if (existing.isPresent()) return existing.get();
        SingleVisitEntitlement entitlement = new SingleVisitEntitlement();
        entitlement.setTransaction(transaction);
        entitlement.setPurchasedQuantity(transaction.getQuantity());
        entitlement.setCreatedAt(Instant.now());
        entitlement.setCreatedBy(actorId);
        return entitlements.save(entitlement);
    }

    @Transactional
    public SingleVisitEntitlementUsage redeem(Long entitlementId, Long appointmentId, Long treatingStaffId,
                                               Patient expectedPatient, Long actorId) {
        SingleVisitEntitlement entitlement = entitlements.findByIdForUpdate(entitlementId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสิทธิ์บริการรายครั้ง"));
        SalesTransaction sale = entitlement.getTransaction();
        require(sale.isTransactionStatus(), "สิทธิ์บริการนี้ถูกยกเลิกแล้ว");
        require(sale.getTransactionType() == TransactionType.SingleVisit, "รายการนี้ไม่ใช่บริการรายครั้ง");
        require(sale.getPatient().getId().equals(expectedPatient.getId()), "สิทธิ์นี้เป็นของผู้ป่วยรายอื่น");
        require(!usages.existsByEntitlementIdAndAppointmentId(entitlementId, appointmentId),
                "นัดนี้ใช้สิทธิ์บริการนี้ไปแล้ว");
        Balance balance = balance(entitlement);
        require(balance.remaining() > 0, "สิทธิ์บริการรายครั้งหมดแล้ว");

        Appointment appointment = appointments.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบนัดหมาย"));
        Staff treating = staff.findById(treatingStaffId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบนักกายภาพผู้รักษา"));
        PatientVisit visit = visits.createForService(appointmentId, expectedPatient, sale.getBranch(), treating, actorId);

        SingleVisitEntitlementUsage use = new SingleVisitEntitlementUsage();
        use.setEntitlement(entitlement);
        use.setAppointment(appointment);
        use.setVisit(visit);
        use.setUsedAt(Instant.now());
        use.setUsedBy(actorId);
        use = usages.saveAndFlush(use);

        PatientVisit completedVisit = visits.completeForService(visit.getId(), appointmentId, actorId);
        BigDecimal netLineAmount = sale.getAmount().subtract(sale.getAllocatedBillDiscountAmount());
        BigDecimal perVisitBase = netLineAmount.divide(BigDecimal.valueOf(entitlement.getPurchasedQuantity()),
                2, RoundingMode.HALF_UP);
        fees.accrue(completedVisit, treating, perVisitBase,
                ClinicTime.dateOf(completedVisit.getVisitDate()), actorId);
        audit.record("single_visit_entitlement_usage", String.valueOf(use.getId()), AuditAction.INSERT, null,
                "ใช้สิทธิ์บริการรายครั้งจากรายการขาย " + sale.getId(), actorId);
        return use;
    }

    @Transactional
    public SingleVisitEntitlementUsage reverse(Long usageId, String reason, Long actorId) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("ต้องระบุเหตุผลที่ย้อนการใช้สิทธิ์");
        SingleVisitEntitlementUsage use = usages.findById(usageId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบประวัติใช้สิทธิ์บริการ"));
        require(use.getReversedAt() == null, "รายการใช้สิทธิ์นี้ถูกย้อนแล้ว");
        use.setReversedAt(Instant.now());
        use.setReversedBy(actorId);
        use.setReversalReason(reason.trim());
        fees.reverse(use.getVisit(), reason, actorId);
        SingleVisitEntitlementUsage saved = usages.save(use);
        audit.record("single_visit_entitlement_usage", String.valueOf(use.getId()), AuditAction.UPDATE,
                "ใช้สิทธิ์", "ย้อนการใช้สิทธิ์: " + reason.trim(), actorId);
        return saved;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
