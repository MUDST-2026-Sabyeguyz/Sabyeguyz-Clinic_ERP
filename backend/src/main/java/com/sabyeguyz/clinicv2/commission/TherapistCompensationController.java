package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.auth.AppUserRepository;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.enums.AllocationEntryType;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.sale.SalesTransactionRepository;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a therapist sees of their own pay: the line behind every baht of a month (course commission, substitute treatment fee,
 * single-visit fee) and their pay adjustments. Read only, and only the caller's own rows.
 */
@Transactional(readOnly = true)
@RestController
@RequestMapping("/api/commission")
@PreAuthorize("isAuthenticated()")
public class TherapistCompensationController {
    private final StaffMonthlyPaymentService paymentService;
    private final StaffPayAdjustmentRepository adjustments;
    private final StaffPayAdjustmentService adjustmentService;
    private final CommissionSchemeService schemeService;
    private final SalesTransactionRepository sales;
    private final CurrentActor actor;
    private final AppUserRepository users;

    public TherapistCompensationController(StaffMonthlyPaymentService paymentService, StaffPayAdjustmentRepository adjustments, StaffPayAdjustmentService adjustmentService,
                                            CommissionSchemeService schemeService, SalesTransactionRepository sales,
                                            CurrentActor actor, AppUserRepository users) {
        this.paymentService = paymentService;
        this.adjustments = adjustments;
        this.adjustmentService = adjustmentService;
        this.schemeService = schemeService;
        this.sales = sales;
        this.actor = actor;
        this.users = users;
    }

    /**
     * One row of the pay of a month. kind = COMMISSION (owner of a course used in the month) or FEE (treatment fee of a
     * substitute or a single visit). The fields of the other kind are null.
     */
    public record Line(String kind, Long id, LocalDate date, String patientName, String patientHn, String item, String role,
                       BigDecimal amount, boolean reversal, boolean pending, String paymentStatus,
                       // commission: how the base and the rate came about
                       String courseNo, Integer totalVisits, BigDecimal grossAmount, BigDecimal lineDiscount, BigDecimal base,
                       BigDecimal rate, String tier, BigDecimal pool, String saleNo, LocalDate saleDate, String saleType,
                       // fee: the rule that was used
                       String feeType, BigDecimal feeValue, BigDecimal feeBase, String ownerName, String treatingName,
                       String visitNo, boolean singleVisit) {}

    public record Lines(String month, BigDecimal courseCommission, BigDecimal singleVisitFee, BigDecimal substituteFee,
                        long pendingSessions, List<Line> lines) {}

    public record ApplicationLine(Long id, String month, BigDecimal amount, String status, String approvedBy,
                                   String approvalReference) {}

    public record MyAdjustment(Long id, String originalMonth, String appliedMonth, BigDecimal amount, String reason, String status,
                               BigDecimal processed, BigDecimal remaining, String reference, String approvedBy,
                               List<ApplicationLine> applications) {}

    @GetMapping("/my-lines")
    public Lines myLines(@RequestParam String month) {
        Staff me = me();
        if (me == null) {
            return new Lines(month, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of());
        }
        StaffMonthlyPaymentService.Totals totals = paymentService.totals(me.getId(), month);
        List<Line> lines = new ArrayList<>();
        for (CommissionAllocationLedger row : totals.ownerRows()) {
            lines.add(commissionLine(row, me));
        }
        for (CommissionAllocationLedger row : totals.feeRows()) {
            lines.add(substituteLine(row));
        }
        for (SingleVisitFeeLedger row : totals.singleRows()) {
            lines.add(singleVisitLine(row));
        }
        lines.sort(Comparator.comparing(Line::date).thenComparing(Line::id));
        return new Lines(month, totals.courseCommission(), totals.singleVisitFee(), totals.substituteFee(),
                totals.pendingSessions(), lines);
    }

    @GetMapping("/my-adjustments")
    public List<MyAdjustment> myAdjustments() {
        Staff me = me();
        if (me == null) {
            return List.of();
        }
        return adjustments.findByStaffIdOrderByIdDesc(me.getId()).stream().map(this::toAdjustment).toList();
    }

    // ---------- lines ----------

    private Line commissionLine(CommissionAllocationLedger row, Staff me) {
        com.sabyeguyz.clinicv2.catalog.Course course = row.getCourse();
        SalesTransaction sale = course.getSaleTransaction();
        boolean locked = course.getCommissionStatus() == CommissionStatus.Locked;
        BigDecimal rate = locked ? course.getLockedCommissionRate() : null;
        BigDecimal discount = sale == null ? BigDecimal.ZERO : sale.getLineDiscountAmount();
        BigDecimal gross = sale == null ? course.getCoursePrice() : sale.getAmount();
        boolean sameStaff = row.getTreatingStaff().getId().equals(row.getOwnerStaff().getId());
        Patient patient = row.getPatient();
        return new Line("COMMISSION", row.getId(), row.getVisitDate(), patientName(patient), patient.getPatientHn(),
                course.getCourseName(), sameStaff ? "เจ้าของคอร์ส + ผู้รักษา" : "เจ้าของคอร์ส (มีผู้รักษาแทน)",
                row.getOwnerNetCommission(), row.getEntryType() == AllocationEntryType.Reversal, false,
                status(row.getOwnerMonthlyPayment()),
                course.getCourseNo(), course.getTotalVisits(), gross, discount, course.getCoursePrice(), rate, tierName(course, rate),
                course.getTotalCourseCommissionPool(), sale == null || sale.getReceipt() == null ? null : sale.getReceipt().getReceiptNo(),
                sale == null ? null : sale.getTransactionDate().atZone(com.sabyeguyz.clinicv2.config.ClinicTime.ZONE).toLocalDate(),
                "คอร์ส",
                null, null, null, null, null, null, false);
    }

    private Line substituteLine(CommissionAllocationLedger row) {
        Patient patient = row.getPatient();
        return new Line("FEE", row.getId(), row.getVisitDate(), patientName(patient), patient.getPatientHn(),
                row.getCourse().getCourseName(), "ผู้รักษาแทน", row.getTreatmentFeeAmount(),
                row.getEntryType() == AllocationEntryType.Reversal, false, status(row.getTreatingMonthlyPayment()),
                row.getCourse().getCourseNo(), null, null, null, null, null, null, null, null, null, null,
                feeType(row.getTreatmentFeeType()), row.getTreatmentFeeRateOrAmount(), row.getTreatmentFeeCalculationBase(),
                staffName(row.getOwnerStaff()), staffName(row.getTreatingStaff()), "USG-" + row.getVisitUsage().getId(), false);
    }

    private Line singleVisitLine(SingleVisitFeeLedger row) {
        com.sabyeguyz.clinicv2.appointment.PatientVisit visit = row.getVisit();
        Patient patient = visit.getPatient();
        List<SalesTransaction> visitSales = sales.findByVisitId(visit.getId()).stream()
                .filter(tx -> tx.getTransactionType() == TransactionType.SingleVisit).toList();
        SalesTransaction sale = visitSales.isEmpty() ? null : visitSales.get(0);
        return new Line("FEE", row.getId(), row.getServiceDate(), patientName(patient), patient.getPatientHn(),
                sale == null ? "บริการรายครั้ง" : sale.getItemNameSnapshot(), "ผู้รักษาจริง", row.getFeeAmount(),
                row.getEntryType() == AllocationEntryType.Reversal, false, status(row.getMonthlyPayment()),
                null, null, sale == null ? null : sale.getAmount(), sale == null ? null : sale.getLineDiscountAmount(), null, null,
                null, null, sale == null || sale.getReceipt() == null ? null : sale.getReceipt().getReceiptNo(),
                sale == null ? null : sale.getTransactionDate().atZone(com.sabyeguyz.clinicv2.config.ClinicTime.ZONE).toLocalDate(),
                "รายครั้ง",
                feeType(row.getFeeType()), row.getFeeValue(), row.getCalculationBase(),
                staffName(visit.getAssignedStaff() == null ? row.getStaff() : visit.getAssignedStaff()), staffName(row.getStaff()),
                "VIS-" + visit.getId(), true);
    }

    private String tierName(com.sabyeguyz.clinicv2.catalog.Course course, BigDecimal rate) {
        if (rate == null || course.getCommissionScheme() == null) {
            return null;
        }
        return schemeService.tiersOf(course.getCommissionScheme().getId()).stream()
                .filter(tier -> tier.getCommissionRate().setScale(4, RoundingMode.HALF_UP).compareTo(rate.setScale(4, RoundingMode.HALF_UP)) == 0)
                .map(tier -> "Tier " + tier.getTierOrder()).findFirst().orElse(null);
    }

    private static String feeType(FeeType type) {
        return type == null ? null : type.name();
    }

    private static String status(StaffMonthlyPayment payment) {
        return payment == null ? null : payment.getPaymentStatus().name().toUpperCase(java.util.Locale.ROOT);
    }

    private static String patientName(Patient patient) {
        return (patient.getPatientFname() + " " + patient.getPatientLname()).trim();
    }

    private static String staffName(Staff staff) {
        return staff == null ? null : (staff.getStaffFname() + " " + staff.getStaffLname()).trim();
    }

    // ---------- adjustments ----------

    private MyAdjustment toAdjustment(StaffPayAdjustment adjustment) {
        String reference = adjustment.getVisit() != null ? "Visit " + adjustment.getVisit().getId()
                : adjustment.getCourse() != null ? "คอร์ส " + adjustment.getCourse().getCourseNo()
                : adjustment.getSaleTransaction() != null ? "รายการขาย " + adjustment.getSaleTransaction().getId()
                : adjustment.getSingleVisitFee() != null ? "ค่ามือ " + adjustment.getSingleVisitFee().getId()
                : adjustment.getAllocation() != null ? "รายการคอมมิชชัน " + adjustment.getAllocation().getId() : null;
        List<ApplicationLine> applications = paymentApplications(adjustment.getId());
        return new MyAdjustment(adjustment.getId(), adjustment.getOriginalMonth(), adjustment.getAppliedMonth(),
                adjustment.getAmount(), adjustment.getReason(), adjustment.getAdjustmentStatus().name(),
                adjustment.getProcessedAmount(), adjustment.getRemainingAmount(), reference, approver(adjustment.getApprovedBy()),
                applications);
    }

    private List<ApplicationLine> paymentApplications(Long adjustmentId) {
        return adjustmentService.applicationsOf(adjustmentId).stream()
                .map(application -> new ApplicationLine(application.getId(),
                        application.getMonthlyPayment() == null ? null : application.getMonthlyPayment().getPaymentMonth(),
                        application.getAmount(), application.getApplicationStatus().name(), approver(application.getApprovedBy()),
                        application.getApprovalReference())).toList();
    }

    private String approver(Long userId) {
        if (userId == null) {
            return null;
        }
        return users.findById(userId).map(user -> staffName(user.getStaff())).orElse(null);
    }

    private Staff me() {
        return users.findById(actor.requireId()).map(user -> user.getStaff()).orElse(null);
    }
}
