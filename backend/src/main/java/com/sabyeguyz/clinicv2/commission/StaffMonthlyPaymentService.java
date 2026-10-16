package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.course.CourseVisitUsageRepository;
import com.sabyeguyz.clinicv2.enums.AdjustmentStatus;
import com.sabyeguyz.clinicv2.enums.ApplicationStatus;
import com.sabyeguyz.clinicv2.enums.ClosingStatus;
import com.sabyeguyz.clinicv2.enums.PaymentStatus;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The monthly payment of one staff member (Data Dictionary: Staff_monthly_payment): what the person earned from
 * treatments of that month (month of the treatment, not of the course sale) plus the adjustments applied in it.
 *
 * <ul>
 *   <li>Owner commission = Owner commission of the sessions the person treated that month ({@code owner_net_commission} of
 *       the allocation rows, reversals included); it is unknown (NULL, never 0) while a session of the person's courses
 *       treated in the month still waits for the sale month to be closed.</li>
 *   <li>Single-visit fee and substitute treatment fee come from their ledgers.</li>
 *   <li>Adjustment = approved adjustment applications reserved (Draft) for the month; a deduction never exceeds what was earned,
 *       the rest is carried to a later month.</li>
 *   <li>Open (editable, recalculated) -> Closed (amounts frozen, waiting for payment, Manager can reopen) -> Paid (locked).
 *       Paying also finalizes the sale-month closings the Owner commission depends on and turns the applied adjustments
 *       into Applied, all in one transaction.</li>
 * </ul>
 */
@Service
public class StaffMonthlyPaymentService {
    private final StaffMonthlyPaymentRepository payments;
    private final CommissionAllocationLedgerRepository allocations;
    private final SingleVisitFeeLedgerRepository singleVisitFees;
    private final CourseVisitUsageRepository usages;
    private final AdjustmentApplicationRepository applications;
    private final StaffPayAdjustmentRepository adjustments;
    private final MonthlyCommissionClosingRepository closings;
    private final MonthlyCommissionClosingService closingService;
    private final StaffRepository staff;

    public StaffMonthlyPaymentService(StaffMonthlyPaymentRepository payments, CommissionAllocationLedgerRepository allocations,
                                       SingleVisitFeeLedgerRepository singleVisitFees, CourseVisitUsageRepository usages,
                                       AdjustmentApplicationRepository applications, StaffPayAdjustmentRepository adjustments,
                                       MonthlyCommissionClosingRepository closings, MonthlyCommissionClosingService closingService,
                                       StaffRepository staff) {
        this.payments = payments;
        this.allocations = allocations;
        this.singleVisitFees = singleVisitFees;
        this.usages = usages;
        this.applications = applications;
        this.adjustments = adjustments;
        this.closings = closings;
        this.closingService = closingService;
        this.staff = staff;
    }

    /** What a staff member earned in a month, worked out from the ledgers. Null amounts = not fully calculated yet. */
    public record Totals(BigDecimal courseCommission, BigDecimal singleVisitFee, BigDecimal substituteFee, long pendingSessions,
                         List<CommissionAllocationLedger> ownerRows, List<CommissionAllocationLedger> feeRows,
                         List<SingleVisitFeeLedger> singleRows) {
        public BigDecimal earned() {
            return courseCommission == null ? null : courseCommission.add(singleVisitFee).add(substituteFee);
        }
    }

    /** The money earned in the month, read live from the ledgers. */
    @Transactional(readOnly = true)
    public Totals totals(Long staffId, String month) {
        LocalDate from = first(month);
        LocalDate to = YearMonth.parse(month).atEndOfMonth();
        List<CommissionAllocationLedger> ownerRows = allocations.findOwnerRows(staffId, from, to);
        List<CommissionAllocationLedger> feeRows = allocations.findTreatingFeeRows(staffId, from, to);
        List<SingleVisitFeeLedger> singleRows = singleVisitFees.findByStaffIdAndServiceDateBetweenOrderByIdAsc(staffId, from, to);
        BigDecimal owner = ownerRows.stream().map(CommissionAllocationLedger::getOwnerNetCommission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal substitute = feeRows.stream().map(CommissionAllocationLedger::getTreatmentFeeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal single = singleRows.stream().map(SingleVisitFeeLedger::getFeeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long pending = usages.countPendingOwnerNet(staffId, from, to);
        return new Totals(pending > 0 ? null : owner, single, substitute, pending, ownerRows, feeRows, singleRows);
    }

    /** Staff who have something to pay (or a payment already) in the month. */
    @Transactional(readOnly = true)
    public Set<Long> staffWithActivity(String month) {
        LocalDate from = first(month);
        LocalDate to = YearMonth.parse(month).atEndOfMonth();
        Set<Long> ids = new TreeSet<>();
        for (CommissionAllocationLedger row : allocations.findByVisitDateBetweenOrderByVisitDateDescIdDesc(from, to)) {
            ids.add(row.getOwnerStaff().getId());
            if (row.getTreatmentFeeAmount().signum() != 0) ids.add(row.getTreatingStaff().getId());
        }
        singleVisitFees.findByServiceDateBetween(from, to).forEach(row -> ids.add(row.getStaff().getId()));
        // the Owner of a session whose commission is still unknown has a payment too (shown as not calculated)
        usages.findCompletedBetween(from, to).stream().filter(usage -> usage.getOwnerNetCommission() == null)
                .forEach(usage -> ids.add(usage.getCourse().getSellerStaff().getId()));
        payments.findByPaymentMonthOrderByIdAsc(month).forEach(payment -> ids.add(payment.getStaff().getId()));
        return ids;
    }

    @Transactional(readOnly = true)
    public List<StaffMonthlyPayment> ofMonth(String month) {
        return payments.findByPaymentMonthOrderByIdAsc(month);
    }

    @Transactional(readOnly = true)
    public List<StaffMonthlyPayment> ofStaff(Long staffId) {
        return payments.findByStaffIdOrderByPaymentMonthDesc(staffId);
    }

    @Transactional(readOnly = true)
    public StaffMonthlyPayment find(Long id) {
        return payments.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการสรุปจ่าย"));
    }

    @Transactional(readOnly = true)
    public List<AdjustmentApplication> applicationsOf(Long paymentId) {
        return applications.findByMonthlyPaymentIdOrderByIdAsc(paymentId);
    }

    /** The payment row of the staff and month, created Open when it does not exist. */
    @Transactional
    public StaffMonthlyPayment openFor(Staff person, String month, Long actorId) {
        requireMonth(month);
        return payments.findByStaffIdAndPaymentMonth(person.getId(), month).orElseGet(() -> {
            StaffMonthlyPayment payment = new StaffMonthlyPayment();
            payment.setStaff(person);
            payment.setPaymentMonth(month);
            payment.setPaymentStatus(PaymentStatus.Open);
            payment.setStatusChangedBy(actorId);
            payment.setStatusChangedAt(Instant.now());
            return payments.save(payment);
        });
    }

    /** Creates or refreshes the Open payment of every staff member with something to pay in the month. */
    @Transactional
    public List<StaffMonthlyPayment> prepare(String month, Long actorId) {
        requireMonth(month);
        Set<Long> ids = new LinkedHashSet<>(staffWithActivity(month));
        List<StaffMonthlyPayment> result = new ArrayList<>();
        for (Long id : ids) {
            Staff person = staff.findById(id).orElseThrow();
            StaffMonthlyPayment payment = openFor(person, month, actorId);
            if (payment.getPaymentStatus() == PaymentStatus.Open) {
                refresh(payment);
            }
            result.add(payment);
        }
        return result;
    }

    /** Recalculates an Open payment (preview) and links the ledger rows of the month to it. */
    @Transactional
    public StaffMonthlyPayment refresh(StaffMonthlyPayment payment) {
        requireStatus(payment, PaymentStatus.Open, "แก้ไข/คำนวณใหม่ได้เฉพาะงวดที่ยังเป็น Open");
        Totals totals = totals(payment.getStaff().getId(), payment.getPaymentMonth());
        link(payment, totals);
        payment.setClosing(closings.findByClosingMonthAndStaffId(payment.getPaymentMonth(), payment.getStaff().getId()).orElse(null));
        payment.setCourseCommissionAmount(totals.courseCommission());
        payment.setSingleVisitFeeAmount(totals.singleVisitFee());
        payment.setSubstituteFeeAmount(totals.substituteFee());
        BigDecimal earned = totals.earned();
        BigDecimal adjustment = earned == null ? previewAdjustment(payment, BigDecimal.ZERO)
                : previewAdjustment(payment, earned);
        payment.setAdjustmentAmount(adjustment);
        payment.setNetPaymentAmount(earned == null ? null : earned.add(adjustment));
        return payments.save(payment);
    }

    /** Open -> Closed: needs every amount to be known; the deductions are limited to what was earned. */
    @Transactional
    public StaffMonthlyPayment close(Long paymentId, Long actorId) {
        StaffMonthlyPayment payment = find(paymentId);
        requireStatus(payment, PaymentStatus.Open, "ปิดงวดได้เฉพาะงวดที่เป็น Open");
        refresh(payment);
        Totals totals = totals(payment.getStaff().getId(), payment.getPaymentMonth());
        if (totals.courseCommission() == null) {
            throw new IllegalStateException("ยังปิดงวดไม่ได้: มี " + totals.pendingSessions()
                    + " ครั้งที่ยังไม่ทราบค่าคอมมิชชันเพราะเดือนขายคอร์สยังไม่ถูกปิดยอด ห้ามนับเป็น 0");
        }
        BigDecimal adjustment = limitDeductions(payment, totals.earned(), true);
        payment.setAdjustmentAmount(adjustment);
        payment.setNetPaymentAmount(totals.earned().add(adjustment));
        payment.setPaymentStatus(PaymentStatus.Closed);
        stamp(payment, actorId);
        return payments.save(payment);
    }

    /** Closed -> Open (before payment): the amounts are calculated again from the ledgers. */
    @Transactional
    public StaffMonthlyPayment reopen(Long paymentId, Long actorId) {
        StaffMonthlyPayment payment = find(paymentId);
        requireStatus(payment, PaymentStatus.Closed, "Reopen ได้เฉพาะงวดที่เป็น Closed (งวด Paid แล้วใช้ Adjustment)");
        payment.setPaymentStatus(PaymentStatus.Open);
        stamp(payment, actorId);
        return refresh(payments.save(payment));
    }

    /**
     * Closed -> Paid (Manager): the payout is confirmed. In one transaction the sale-month closings behind the Owner
     * commission are finalized (a rate that can still be reopened must not sit under money already paid), the approved
     * adjustment applications become Applied, every amount is frozen and the payment is stamped as paid.
     */
    @Transactional
    public StaffMonthlyPayment pay(Long paymentId, Long actorId) {
        return pay(paymentId, null, null, actorId);
    }

    /** Same, with the date the money was paid (today when null; never in the future) and an optional reference. */
    @Transactional
    public StaffMonthlyPayment pay(Long paymentId, java.time.LocalDate paidDate, String reference, Long actorId) {
        java.time.LocalDate today = com.sabyeguyz.clinicv2.config.ClinicTime.today();
        java.time.LocalDate date = paidDate == null ? today : paidDate;
        if (date.isAfter(today)) throw new IllegalArgumentException("วันที่จ่ายต้องไม่เป็นวันในอนาคต");
        String ref = reference == null || reference.isBlank() ? null : reference.trim();
        if (ref != null && ref.length() > 100) throw new IllegalArgumentException("เลขที่อ้างอิงยาวเกิน 100 ตัวอักษร");
        StaffMonthlyPayment payment = find(paymentId);
        requireStatus(payment, PaymentStatus.Closed, "ยืนยันจ่ายได้เฉพาะงวดที่เป็น Closed");
        Totals totals = totals(payment.getStaff().getId(), payment.getPaymentMonth());
        if (totals.courseCommission() == null) {
            throw new IllegalStateException("ยืนยันจ่ายไม่ได้: ยังมีค่าคอมมิชชันที่ไม่ทราบอัตรา ห้ามนับเป็น 0");
        }
        BigDecimal earned = totals.earned();
        if (payment.getNetPaymentAmount() == null || payment.getCourseCommissionAmount().compareTo(totals.courseCommission()) != 0
                || payment.getSingleVisitFeeAmount().compareTo(totals.singleVisitFee()) != 0
                || payment.getSubstituteFeeAmount().compareTo(totals.substituteFee()) != 0) {
            throw new IllegalStateException("ข้อมูลค่าตอบแทนของเดือนนี้เปลี่ยนไปหลังปิดงวด ให้ Reopen งวดแล้วปิดงวดใหม่");
        }
        // the sale months whose rate the Owner commission uses must be finalized together with the payment
        Set<String> saleMonths = new TreeSet<>();
        for (CommissionAllocationLedger row : totals.ownerRows()) {
            if (row.getOwnerNetCommission().signum() != 0) saleMonths.add(row.getCourse().getSaleMonth());
        }
        for (String saleMonth : saleMonths) {
            MonthlyCommissionClosing closing = closings.findByClosingMonthAndStaffId(saleMonth, payment.getStaff().getId())
                    .orElseThrow(() -> new IllegalStateException("ไม่พบการปิดยอดของเดือนขาย " + saleMonth));
            closingService.finalizeClosing(closing, actorId);
        }
        link(payment, totals);
        BigDecimal adjustment = limitDeductions(payment, earned, true);
        for (AdjustmentApplication application : applications.findByMonthlyPaymentIdOrderByIdAsc(payment.getId())) {
            if (application.getApplicationStatus() == ApplicationStatus.Draft && application.getApprovedBy() != null) {
                application.setApplicationStatus(ApplicationStatus.Applied);
                applications.save(application);
                settle(application.getAdjustment());
            }
        }
        payment.setClosing(closings.findByClosingMonthAndStaffId(payment.getPaymentMonth(), payment.getStaff().getId()).orElse(null));
        payment.setAdjustmentAmount(adjustment);
        payment.setNetPaymentAmount(earned.add(adjustment));
        payment.setPaymentStatus(PaymentStatus.Paid);
        payment.setPaidAt(Instant.now());
        payment.setPaidDate(date);
        payment.setPaymentReference(ref);
        stamp(payment, actorId);
        return payments.save(payment);
    }

    /** Totals of an adjustment after its Applied applications: processed, remaining, status. */
    @Transactional
    public void settle(StaffPayAdjustment adjustment) {
        BigDecimal processed = applications.findByAdjustmentIdOrderByIdAsc(adjustment.getId()).stream()
                .filter(application -> application.getApplicationStatus() == ApplicationStatus.Applied)
                .map(application -> application.getAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        adjustment.setProcessedAmount(processed);
        adjustment.setRemainingAmount(adjustment.getAmount().abs().subtract(processed));
        if (adjustment.getAdjustmentStatus() != AdjustmentStatus.Cancelled && adjustment.getAdjustmentStatus() != AdjustmentStatus.ExternalReview) {
            adjustment.setAdjustmentStatus(adjustment.getRemainingAmount().signum() == 0 ? AdjustmentStatus.Applied
                    : processed.signum() > 0 ? AdjustmentStatus.PartiallyApplied : AdjustmentStatus.Approved);
        }
        adjustments.save(adjustment);
    }

    private void link(StaffMonthlyPayment payment, Totals totals) {
        for (CommissionAllocationLedger row : totals.ownerRows()) {
            if (row.getOwnerMonthlyPayment() == null) {
                row.setOwnerMonthlyPayment(payment);
                allocations.save(row);
            }
        }
        for (CommissionAllocationLedger row : totals.feeRows()) {
            if (row.getTreatingMonthlyPayment() == null) {
                row.setTreatingMonthlyPayment(payment);
                allocations.save(row);
            }
        }
        for (SingleVisitFeeLedger row : totals.singleRows()) {
            if (row.getMonthlyPayment() == null) {
                row.setMonthlyPayment(payment);
                singleVisitFees.save(row);
            }
        }
    }

    /** Sum of the approved Draft applications of the payment, deductions limited to what was earned (no change is stored). */
    private BigDecimal previewAdjustment(StaffMonthlyPayment payment, BigDecimal earned) {
        return limitDeductions(payment, earned, false);
    }

    /**
     * Adds the approved Draft applications of the payment: additions in full, deductions only up to what is left of the
     * earnings. With {@code store} the amount of a limited deduction is reduced (and an empty one cancelled), so the rest
     * stays on the adjustment for a later month.
     */
    private BigDecimal limitDeductions(StaffMonthlyPayment payment, BigDecimal earned, boolean store) {
        List<AdjustmentApplication> drafts = applications.findByMonthlyPaymentIdOrderByIdAsc(payment.getId()).stream()
                .filter(application -> application.getApplicationStatus() == ApplicationStatus.Draft && application.getApprovedBy() != null)
                .sorted(Comparator.comparing(AdjustmentApplication::getId)).toList();
        BigDecimal available = earned;
        for (AdjustmentApplication application : drafts) {
            if (application.getAmount().signum() > 0) available = available.add(application.getAmount());
        }
        BigDecimal total = BigDecimal.ZERO;
        for (AdjustmentApplication application : drafts) {
            if (application.getAmount().signum() > 0) {
                total = total.add(application.getAmount());
                continue;
            }
            BigDecimal wanted = application.getAmount().abs();
            BigDecimal allowed = wanted.min(available.max(BigDecimal.ZERO));
            available = available.subtract(allowed);
            if (allowed.signum() == 0) {
                if (store) {
                    application.setApplicationStatus(ApplicationStatus.Cancelled);
                    applications.save(application);
                }
                continue;
            }
            if (store && allowed.compareTo(wanted) != 0) {
                application.setAmount(allowed.negate());
                applications.save(application);
            }
            total = total.subtract(allowed);
        }
        return total;
    }

    private static void stamp(StaffMonthlyPayment payment, Long actorId) {
        payment.setStatusChangedBy(actorId);
        payment.setStatusChangedAt(Instant.now());
    }

    private static void requireStatus(StaffMonthlyPayment payment, PaymentStatus expected, String message) {
        if (payment.getPaymentStatus() != expected) {
            throw new IllegalStateException(message);
        }
    }

    /** The month a date falls in (yyyy-mm), in clinic time. */
    static String monthOf(LocalDate date) {
        return YearMonth.from(date).toString();
    }

    private static LocalDate first(String month) {
        requireMonth(month);
        return YearMonth.parse(month).atDay(1);
    }

    static void requireMonth(String month) {
        if (month == null || !month.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("เดือนต้องเป็นรูปแบบ yyyy-mm");
        }
    }
}
