package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.course.CourseVisitUsage;
import com.sabyeguyz.clinicv2.course.CourseVisitUsageRepository;
import com.sabyeguyz.clinicv2.enums.AllocationEntryType;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.enums.PaymentStatus;
import com.sabyeguyz.clinicv2.enums.UsageStatus;
import com.sabyeguyz.clinicv2.staff.Staff;

/**
 * What a course owes its Owner PT and its substitute PTs, session by session (Data Dictionary: Course_visit_usage,
 * Commission_allocation_ledger).
 *
 * <ul>
 *   <li>The Owner commission of a session is not known until the sale month is closed and the course's rate is locked:
 *       until then {@code owner_net_commission} and {@code commission_allocation_per_visit} of an Owner-treated session
 *       stay NULL (never 0) and no allocation row exists. {@link #settle} fills them when the month is closed.</li>
 *   <li>A session treated by someone else, or by an Owner who has already left, earns the Owner 0 at once: that is known
 *       without the rate, and the substitute's treatment fee is independent of the closing.</li>
 *   <li>An allocation is never edited. Before payment a correction is a Reversal of the old allocation plus a new
 *       allocation of the next version ({@link #recalculate}); once a payment is Paid it can only be fixed with a pay
 *       adjustment.</li>
 * </ul>
 */
@Service
public class CourseCommissionService {
    private final CourseVisitUsageRepository usages;
    private final CommissionAllocationLedgerRepository allocations;
    private final CourseRepository courses;
    private final TreatmentFeeRuleResolver feeResolver;

    public CourseCommissionService(
            CourseVisitUsageRepository usages,
            CommissionAllocationLedgerRepository allocations,
            CourseRepository courses,
            TreatmentFeeRuleResolver feeResolver) {
        this.feeResolver = feeResolver;
        this.usages = usages;
        this.allocations = allocations;
        this.courses = courses;
    }

    /** Whether the course's rate is locked, so that the Owner commission of every session can be worked out. */
    public static boolean isRateKnown(Course course) {
        return course.getCommissionStatus() == CommissionStatus.Locked && course.getTotalCourseCommissionPool() != null;
    }

    /** The rate of the session at a position in the course; the course's rate must be known. */
    public static BigDecimal rateAt(
            Course course,
            int position) {
        return CommissionAllocationCalculator.forPosition(
                course.getTotalCourseCommissionPool(),
                course.getTotalVisits(),
                position);
    }

    /** Whether the Owner earns the commission of a session treated by {@code treating} on {@code visitDate}. */
    public static boolean ownerEarns(
            Course course,
            Staff treating,
            LocalDate visitDate) {
        Staff owner = course.getOwnerStaff();
        boolean ownerTreated = treating != null && treating.getId().equals(owner.getId());
        boolean ownerLeft = owner.getTerminationDate() != null && visitDate.isAfter(owner.getTerminationDate());

        return ownerTreated && !ownerLeft;
    }

    /** The Owner's earning for a new session: the position's rate, 0 when the Owner does not earn it, NULL when unknown. */
    public static BigDecimal ownerNetOf(
            Course course,
            int position,
            Staff treating,
            LocalDate visitDate) {
        if (!ownerEarns(course, treating, visitDate)) {
            return BigDecimal.ZERO;
        }

        return isRateKnown(course) ? rateAt(course, position) : null;
    }

    /**
     * Writes one allocation row for a usage. The treatment fee snapshot is the one on the usage (new usage) or on the
     * allocation it replaces ({@code previous}, a recalculation).
     */
    public CommissionAllocationLedger recordAllocation(
            CourseVisitUsage usage,
            BigDecimal ownerNet,
            int version,
            CommissionAllocationLedger previous,
            Long actorId) {
        Course course = usage.getCourse();
        CommissionAllocationLedger allocation = new CommissionAllocationLedger();
        allocation.setVisitUsage(usage);
        allocation.setEntryType(AllocationEntryType.Allocation);
        allocation.setCourse(course);
        allocation.setPatient(usage.getPatient());
        allocation.setOwnerStaff(course.getOwnerStaff());
        allocation.setTreatingStaff(usage.getTreatingStaff());
        allocation.setVisitDate(usage.getVisitDate().toLocalDate());
        allocation.setAllocationVersion(version);

        if (previous != null) {
            allocation.setTreatmentFeeRule(previous.getTreatmentFeeRule());
            allocation.setTreatmentFeeType(previous.getTreatmentFeeType());
            allocation.setTreatmentFeeRateOrAmount(previous.getTreatmentFeeRateOrAmount());
            allocation.setTreatmentFeeCalculationBase(previous.getTreatmentFeeCalculationBase());
            allocation.setOwnerMonthlyPayment(null);
            allocation.setTreatingMonthlyPayment(null);
        } else if (usage.getTreatmentFeeRule() != null) {
            allocation.setTreatmentFeeRule(usage.getTreatmentFeeRule());
            allocation.setTreatmentFeeType(usage.getTreatmentFeeRule().getFeeType());
            allocation.setTreatmentFeeRateOrAmount(usage.getTreatmentFeeRule().getFeeValue());
            allocation.setTreatmentFeeCalculationBase(
                    usage.getTreatmentFeeRule().getFeeType() == com.sabyeguyz.clinicv2.enums.FeeType.Fixed ? null
                            : feeResolver.perVisitBase(course));
        }

        allocation.setTreatmentFeeAmount(usage.getSubstituteTreatmentFeeAmount());
        allocation.setOwnerNetCommission(ownerNet);
        allocation.setAllocationCreateAt(Instant.now());
        allocation.setAllocationCreateBy(actorId);

        return allocations.save(allocation);
    }

    /**
     * The month the course was sold in has just been closed and its rate locked: every session already used gets its
     * rate and, when the Owner treated it, its Owner commission plus the allocation row that was waiting for the rate.
     */
    @Transactional
    public void settle(
            Course course,
            Long actorId) {
        if (!isRateKnown(course)) {
            return;
        }

        for (CourseVisitUsage usage : completedUsages(course)) {
            BigDecimal rate = rateAt(course, usage.getUsageSequence());
            usage.setCommissionAllocationPerVisit(rate);

            if (usage.getOwnerNetCommission() == null) {
                boolean earns = ownerEarns(course, usage.getTreatingStaff(), usage.getVisitDate().toLocalDate());
                BigDecimal ownerNet = earns ? rate : BigDecimal.ZERO;
                usage.setOwnerNetCommission(ownerNet);
                recordAllocation(usage, ownerNet, nextVersion(usage), null, actorId);
            }

            usage.setUsageUpdateAt(Instant.now());
            usage.setUsageUpdateBy(actorId);
            usages.save(usage);
        }

        refreshTotals(course);
        courses.save(course);
    }

    /**
     * The sale month of the course is reopened (before payment): the Owner commission allocated at the old rate is
     * reversed and the sessions go back to "rate unknown" until the month is closed again, which allocates the next
     * version. Allocations whose commission sits in a payment that is Closed or Paid are refused.
     */
    @Transactional
    public void unlock(
            Course course,
            String reason,
            Long actorId) {
        for (CourseVisitUsage usage : completedUsages(course)) {
            boolean earns = ownerEarns(course, usage.getTreatingStaff(), usage.getVisitDate().toLocalDate());

            if (earns) {
                currentAllocation(usage).ifPresent(original -> {
                    requireNotPaid(original);

                    if (original.getOwnerMonthlyPayment() != null
                            && original.getOwnerMonthlyPayment().getPaymentStatus() != PaymentStatus.Open) {
                        throw new IllegalStateException("ค่าตอบแทนอยู่ในงวดจ่ายที่ปิดแล้ว ให้ Reopen งวดจ่ายก่อน");
                    }

                    allocations.save(reversalOf(original, reason, actorId));
                });
                usage.setOwnerNetCommission(null);
            }

            usage.setCommissionAllocationPerVisit(null);
            usage.setUsageUpdateAt(Instant.now());
            usage.setUsageUpdateBy(actorId);
            usages.save(usage);
        }

        course.setCommissionStatus(CommissionStatus.Provisional);
        course.setLockedCommissionRate(null);
        course.setTotalCourseCommissionPool(null);
        course.setCommissionAllocationPerVisit(null);
        course.setCommissionGeneratedDate(null);
        refreshTotals(course);
        courses.save(course);
    }

    /**
     * The rate of the course changed before it was paid (a month reopened, a manual override): the commission already
     * allocated is reversed and a new version is allocated at the new rate. A session whose Owner commission is already
     * in a Paid payment cannot be touched here.
     */
    @Transactional
    public void recalculate(
            Course course,
            String reason,
            Long actorId) {
        if (!isRateKnown(course)) {
            return;
        }

        for (CourseVisitUsage usage : completedUsages(course)) {
            Optional<CommissionAllocationLedger> current = currentAllocation(usage);
            BigDecimal rate = rateAt(course, usage.getUsageSequence());
            boolean earns = ownerEarns(course, usage.getTreatingStaff(), usage.getVisitDate().toLocalDate());
            BigDecimal ownerNet = earns ? rate : BigDecimal.ZERO;
            usage.setCommissionAllocationPerVisit(rate);

            if (current.isPresent() && current.get().getOwnerNetCommission().compareTo(ownerNet) == 0) {
                usages.save(usage);
                continue; // nothing changes for this session: keep the allocation as it is
            }

            if (current.isPresent()) {
                CommissionAllocationLedger original = current.get();
                requireNotPaid(original);
                CommissionAllocationLedger reversal = reversalOf(original, reason, actorId);
                allocations.save(reversal);
                recordAllocation(usage, ownerNet, original.getAllocationVersion() + 1, original, actorId);
            } else {
                recordAllocation(usage, ownerNet, nextVersion(usage), null, actorId);
            }

            usage.setOwnerNetCommission(ownerNet);
            usage.setUsageUpdateAt(Instant.now());
            usage.setUsageUpdateBy(actorId);
            usages.save(usage);
        }

        refreshTotals(course);
        courses.save(course);
    }

    /** The Reversal that cancels an allocation (same usage and version, every amount the opposite). */
    public CommissionAllocationLedger reversalOf(
            CommissionAllocationLedger original,
            String reason,
            Long actorId) {
        CommissionAllocationLedger reversal = new CommissionAllocationLedger();
        reversal.setEntryType(AllocationEntryType.Reversal);
        reversal.setReversesAllocation(original);
        reversal.setAllocationVersion(original.getAllocationVersion());
        reversal.setReversalReason(reason.trim());
        reversal.setVisitUsage(original.getVisitUsage());
        reversal.setCourse(original.getCourse());
        reversal.setPatient(original.getPatient());
        reversal.setOwnerStaff(original.getOwnerStaff());
        reversal.setTreatingStaff(original.getTreatingStaff());
        reversal.setVisitDate(original.getVisitDate());
        reversal.setTreatmentFeeRule(original.getTreatmentFeeRule());
        reversal.setTreatmentFeeType(original.getTreatmentFeeType());
        reversal.setTreatmentFeeRateOrAmount(original.getTreatmentFeeRateOrAmount());
        reversal.setTreatmentFeeCalculationBase(original.getTreatmentFeeCalculationBase());
        reversal.setTreatmentFeeAmount(original.getTreatmentFeeAmount().negate());
        reversal.setOwnerNetCommission(original.getOwnerNetCommission().negate());
        reversal.setAllocationCreateAt(Instant.now());
        reversal.setAllocationCreateBy(actorId);

        return reversal;
    }

    /** The allocation of a usage that has not been reversed yet. */
    public Optional<CommissionAllocationLedger> currentAllocation(CourseVisitUsage usage) {
        List<CommissionAllocationLedger> rows = allocations.findByVisitUsageIdOrderByIdAsc(usage.getId());
        Set<Long> reversed = new HashSet<>();

        for (CommissionAllocationLedger row : rows) {
            if (row.getEntryType() == AllocationEntryType.Reversal
                    && row.getReversesAllocation() != null) {
                reversed.add(row.getReversesAllocation().getId());
            }
        }

        return rows.stream()
                .filter(row -> row.getEntryType() == AllocationEntryType.Allocation && !reversed.contains(row.getId()))
                .reduce((first, second) -> second);
    }

    /** An allocation whose Owner commission or treatment fee is in a Paid payment can only be corrected with an adjustment. */
    public static void requireNotPaid(CommissionAllocationLedger allocation) {
        if (isPaid(allocation.getOwnerMonthlyPayment())
                || isPaid(allocation.getTreatingMonthlyPayment())) {
            throw new IllegalStateException("ค่าตอบแทนรายการนี้ถูกจ่ายแล้ว ไม่สามารถกลับรายการได้ ให้บันทึกเป็น Adjustment ในงวดถัดไป");
        }
    }

    private static boolean isPaid(StaffMonthlyPayment payment) {
        return payment != null && payment.getPaymentStatus() == PaymentStatus.Paid;
    }

    private int nextVersion(CourseVisitUsage usage) {
        return allocations.findByVisitUsageIdOrderByIdAsc(usage.getId()).stream()
                .mapToInt(CommissionAllocationLedger::getAllocationVersion)
                .max()
                .orElse(0) + 1;
    }

    /** The course's cached totals, worked out again from its Completed sessions. */
    @Transactional
    public void refreshTotals(Course course) {
        BigDecimal ownerTotal = BigDecimal.ZERO;
        BigDecimal feeTotal = BigDecimal.ZERO;

        for (CourseVisitUsage usage : completedUsages(course)) {
            if (usage.getOwnerNetCommission() != null) ownerTotal = ownerTotal.add(usage.getOwnerNetCommission());
            feeTotal = feeTotal.add(usage.getSubstituteTreatmentFeeAmount());
        }

        course.setOwnerNetCommissionReleasedTotal(ownerTotal);
        course.setSubstituteTreatmentFeeTotal(feeTotal);
        course.setCommissionOutstanding(outstandingOf(course));
    }

    /**
     * Potential Owner commission of the sessions still to be used: NULL while the rate is unknown, 0 once the course can
     * no longer be used (cancelled, refunded, completed, expired) or its Owner has left, otherwise the rates of the free
     * positions the remaining sessions will take.
     */
    BigDecimal outstandingOf(Course course) {
        if (!isRateKnown(course)) {
            return null;
        }

        Staff owner = course.getOwnerStaff();
        // from the day the Owner leaves no session can earn the Owner anything more (a future leaving date still allows it)
        boolean ownerLeft = owner != null && owner.getTerminationDate() != null
                && !ClinicTime.today().isBefore(owner.getTerminationDate());
        boolean expired = course.getExpiryDate() != null && course.getExpiryDate().isBefore(ClinicTime.today());

        if (course.getCourseStatus() != CourseStatus.Active || expired || ownerLeft) {
            return BigDecimal.ZERO;
        }

        Set<Integer> taken = new HashSet<>();

        for (CourseVisitUsage usage : completedUsages(course)) taken.add(usage.getUsageSequence());
        BigDecimal outstanding = BigDecimal.ZERO;
        int remaining = Math.max(course.getRemainingVisits(), 0);

        for (int position = 1; position <= course.getTotalVisits() && remaining > 0; position++) {
            if (taken.contains(position)) continue;
            outstanding = outstanding.add(rateAt(course, position));
            remaining--;
        }

        return outstanding;
    }

    private List<CourseVisitUsage> completedUsages(Course course) {
        List<CourseVisitUsage> completed = new ArrayList<>();

        for (CourseVisitUsage usage : usages.findByCourseIdOrderByIdDesc(course.getId())) {
            if (usage.getUsageStatus() == UsageStatus.Completed) completed.add(usage);
        }

        completed.sort((a, b) -> Integer.compare(a.getUsageSequence(), b.getUsageSequence()));

        return completed;
    }
}
