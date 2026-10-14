package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.commission.CommissionAllocationLedger;
import com.sabyeguyz.clinicv2.commission.CommissionAllocationLedgerRepository;
import com.sabyeguyz.clinicv2.commission.TreatmentFeeRule;
import com.sabyeguyz.clinicv2.commission.TreatmentFeeRuleResolver;
import com.sabyeguyz.clinicv2.enums.AllocationEntryType;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.enums.UsageStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.sale.TransactionCancellationService;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uses one Course visit and splits the commission between the Case Owner
 * and (if different) the substitute Treating PT — the Financial Allocation
 * Rule, Commission_System_Requirement_Dev §15.2 / §28.
 *
 * <p>Known simplifications, both flagged for the team:</p>
 * <ul>
 *   <li>Overflow policy is hard-coded to CAP_AT_COMMISSION (the doc's own
 *   default recommendation) since System_setting is not wired in yet.</li>
 *   <li>If the Course's rate is not locked yet (Monthly Closing has not
 *   run), this falls back to price × provisionalCommissionRate / totalVisits
 *   so "Buy &amp; Use Same Day" (Workflow Brief §5) still works. That
 *   provisional number can differ from the frozen one Monthly Closing
 *   computes later — the doc does not say how that reconciles.</li>
 * </ul>
 */
@Service
public class CourseVisitUsageService {
    private final CourseVisitUsageRepository usages;
    private final CourseLedgerRepository ledger;
    private final CourseRepository courses;
    private final CommissionAllocationLedgerRepository allocationLedger;
    private final TreatmentFeeRuleResolver treatmentFeeRuleResolver;
    private final CourseEntitlementRepository entitlements;
    private final TransactionCancellationService cancellations;

    public CourseVisitUsageService(CourseVisitUsageRepository usages, CourseLedgerRepository ledger,
                                    CourseRepository courses, CommissionAllocationLedgerRepository allocationLedger,
                                    TreatmentFeeRuleResolver treatmentFeeRuleResolver,
                                    CourseEntitlementRepository entitlements,
                                    TransactionCancellationService cancellations) {
        this.usages = usages;
        this.ledger = ledger;
        this.courses = courses;
        this.allocationLedger = allocationLedger;
        this.treatmentFeeRuleResolver = treatmentFeeRuleResolver;
        this.entitlements = entitlements;
        this.cancellations = cancellations;
    }

    @Transactional
    public CourseVisitUsage use(Course course, PatientVisit visit, Patient usingPatient, Staff treatingStaff,
                                 LocalDate visitDate, Long actorId) {
        require(course != null, "ต้องระบุคอร์ส");
        require(course.getCourseStatus() == com.sabyeguyz.clinicv2.enums.CourseStatus.Active,
                "คอร์สนี้ไม่อยู่ในสถานะพร้อมใช้งาน");
        require(visit != null, "ต้องมีการเช็คอินก่อนจึงจะใช้สิทธิ์คอร์สได้");
        require(usingPatient != null, "ต้องระบุผู้ป่วยที่ใช้สิทธิ์");
        require(treatingStaff != null, "ต้องระบุผู้รักษา");
        require(visitDate != null, "ต้องระบุวันที่ใช้บริการ");
        if (course.getExpiryDate().isBefore(visitDate)) {
            throw new IllegalArgumentException("คอร์สหมดอายุแล้ว");
        }
        if (course.getRemainingVisits() <= 0) {
            throw new IllegalArgumentException("คอร์สใช้สิทธิ์ครบแล้ว");
        }
        long patientBalance = ledger.sumQtyChangeByCourseAndPatient(course.getId(), usingPatient.getId());
        if (patientBalance <= 0) {
            throw new IllegalArgumentException("ผู้ป่วยรายนี้ไม่มีสิทธิ์คงเหลือในคอร์สนี้");
        }

        // The Owner PT is the course's seller. The Owner earns the per-visit commission only when treating the visit
        // personally (and before leaving); a substitute earns the full treatment fee of the rule in force that day,
        // never limited by the commission, and the Owner then gets 0.
        Staff owner = course.getOwnerStaff();
        boolean substituted = !treatingStaff.getId().equals(owner.getId());
        boolean ownerLeft = owner.getTerminationDate() != null && visitDate.isAfter(owner.getTerminationDate());
        BigDecimal allocationPerVisit = commissionAllocationPerVisit(course);

        BigDecimal treatmentFee = BigDecimal.ZERO;
        TreatmentFeeRule appliedRule = null;
        if (substituted) {
            appliedRule = treatmentFeeRuleResolver.resolve(visitDate)
                    .orElseThrow(() -> new IllegalStateException(
                            "ไม่พบ Treatment Fee Rule ที่ใช้ได้ในวันที่ " + visitDate));
            treatmentFee = treatmentFeeRuleResolver.calculateFee(appliedRule, course);
        }
        BigDecimal ownerNet = substituted || ownerLeft ? BigDecimal.ZERO : allocationPerVisit;

        CourseVisitUsage usage = new CourseVisitUsage();
        usage.setVisit(visit);
        usage.setCourse(course);
        usage.setPatient(usingPatient);
        usage.setTreatingStaff(treatingStaff);
        if (visit.getTreatingStaff() == null) {
            visit.setTreatingStaff(treatingStaff);
        }
        usage.setVisitDate(visitDate.atStartOfDay());
        usage.setVisitQty(1);
        usage.setUsageStatus(UsageStatus.Completed);
        usage.setCommissionAllocationPerVisit(allocationPerVisit);
        placeInCourse(usage, course);
        usage.setTreatmentFeeRule(appliedRule);
        usage.setSubstituteTreatmentFeeAmount(treatmentFee);
        usage.setOwnerNetCommission(ownerNet);
        usage.setUsageCreateAt(Instant.now());
        usage.setUsageCreateBy(actorId);
        CourseVisitUsage savedUsage = usages.save(usage);

        CommissionAllocationLedger allocation = new CommissionAllocationLedger();
        allocation.setVisitUsage(savedUsage);
        allocation.setEntryType(AllocationEntryType.Allocation);
        allocation.setCourse(course);
        allocation.setPatient(usingPatient);
        allocation.setOwnerStaff(owner);
        allocation.setTreatingStaff(treatingStaff);
        allocation.setVisitDate(visitDate);
        allocation.setAllocationVersion(1);
        allocation.setTreatmentFeeRule(appliedRule);
        allocation.setTreatmentFeeType(appliedRule == null ? null : appliedRule.getFeeType());
        allocation.setTreatmentFeeRateOrAmount(appliedRule == null ? null : appliedRule.getFeeValue());
        allocation.setTreatmentFeeCalculationBase(appliedRule == null || appliedRule.getFeeType() == FeeType.Fixed
                ? null : treatmentFeeRuleResolver.perVisitBase(course));
        allocation.setTreatmentFeeAmount(treatmentFee);
        allocation.setOwnerNetCommission(ownerNet);
        allocation.setAllocationCreateAt(Instant.now());
        allocation.setAllocationCreateBy(actorId);
        allocationLedger.save(allocation);

        long newPatientBalance = patientBalance - 1;
        CourseLedger treatmentEntry = new CourseLedger();
        treatmentEntry.setCourse(course);
        treatmentEntry.setPatient(usingPatient);
        treatmentEntry.setEntryType(CourseLedgerEntryType.Treatment);
        treatmentEntry.setQtyChange(-1);
        treatmentEntry.setBalanceAfter((int) newPatientBalance);
        treatmentEntry.setVisitUsage(savedUsage);
        treatmentEntry.setLedgerCreateAt(Instant.now());
        treatmentEntry.setLedgerCreateBy(actorId);
        ledger.save(treatmentEntry);

        CourseEntitlement entitlement = entitlementOf(course, usingPatient, patientBalance);
        entitlement.adjustUsed(1);
        entitlements.save(entitlement);

        course.setVisitsUsed(course.getVisitsUsed() + 1);
        course.setRemainingVisits(course.getRemainingVisits() - 1);
        course.setOwnerNetCommissionReleasedTotal(course.getOwnerNetCommissionReleasedTotal().add(ownerNet));
        course.setSubstituteTreatmentFeeTotal(course.getSubstituteTreatmentFeeTotal().add(treatmentFee));
        course.refreshCommissionOutstanding();
        courses.save(course);

        return savedUsage;
    }

    /**
     * Position of the usage inside the course (all users together): the lowest position not held by a Completed usage,
     * so a position freed by a reversal is reused. A usage that fills a Reversed position replaces that usage.
     */
    private void placeInCourse(CourseVisitUsage usage, Course course) {
        List<CourseVisitUsage> existing = usages.findByCourseIdOrderByIdDesc(course.getId());
        Set<Integer> taken = new HashSet<>();
        for (CourseVisitUsage other : existing) {
            if (other.getUsageStatus() == UsageStatus.Completed) taken.add(other.getUsageSequence());
        }
        int position = 1;
        while (taken.contains(position)) position++;
        usage.setUsageSequence(position);
        final int chosen = position;
        Set<Long> alreadyReplaced = new HashSet<>();
        for (CourseVisitUsage other : existing) {
            if (other.getReplacesUsage() != null) alreadyReplaced.add(other.getReplacesUsage().getId());
        }
        existing.stream() // newest first
                .filter(other -> other.getUsageStatus() == UsageStatus.Reversed && other.getUsageSequence() == chosen
                        && !alreadyReplaced.contains(other.getId()))
                .findFirst().ifPresent(usage::setReplacesUsage);
    }

    /**
     * Reverses a Completed usage: the session goes back to the patient and the
     * commission allocation is reversed (never edited). Everything that was
     * added to the Course's running totals is taken off again.
     */
    @Transactional
    public CourseVisitUsage reverse(CourseVisitUsage usage, Permission permission, String reason, Long actorId) {
        require(usage != null, "ต้องระบุรายการใช้สิทธิ์คอร์ส");
        require(usage.getUsageStatus() == UsageStatus.Completed, "รายการใช้สิทธิ์นี้ถูกย้อนไปแล้ว");
        cancellations.recordUsageReversal(usage, permission, reason, actorId);
        Course course = usage.getCourse();
        Patient patient = usage.getPatient();

        CourseLedger treatment = ledger.findByVisitUsageId(usage.getId()).stream()
                .filter(l -> l.getEntryType() == CourseLedgerEntryType.Treatment).findFirst()
                .orElseThrow(() -> new IllegalStateException("ไม่พบรายการหักสิทธิ์ใน Ledger ของการใช้สิทธิ์นี้"));
        long balance = ledger.sumQtyChangeByCourseAndPatient(course.getId(), patient.getId());
        ledger.save(CourseLedgerEntries.reversalOf(treatment, balance, reason.trim(), actorId));

        CommissionAllocationLedger original = allocationLedger
                .findByVisitUsageIdAndEntryType(usage.getId(), AllocationEntryType.Allocation)
                .orElseThrow(() -> new IllegalStateException("ไม่พบรายการจัดสรรคอมมิชชันของการใช้สิทธิ์นี้"));
        CommissionAllocationLedger reversal = new CommissionAllocationLedger();
        reversal.setEntryType(AllocationEntryType.Reversal);
        reversal.setReversesAllocation(original);
        reversal.setAllocationVersion(original.getAllocationVersion());
        reversal.setReversalReason(reason.trim());
        reversal.setVisitUsage(usage);
        reversal.setCourse(course);
        reversal.setPatient(patient);
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
        allocationLedger.save(reversal);

        usage.setUsageStatus(UsageStatus.Reversed);
        usage.setUsageUpdateAt(Instant.now());
        usage.setUsageUpdateBy(actorId);
        usages.save(usage);

        course.setVisitsUsed(course.getVisitsUsed() - 1);
        course.setRemainingVisits(course.getRemainingVisits() + 1);
        course.setOwnerNetCommissionReleasedTotal(
                course.getOwnerNetCommissionReleasedTotal().subtract(original.getOwnerNetCommission()));
        course.setSubstituteTreatmentFeeTotal(
                course.getSubstituteTreatmentFeeTotal().subtract(original.getTreatmentFeeAmount()));
        course.refreshCommissionOutstanding();
        courses.save(course);

        CourseEntitlement entitlement = entitlementOf(course, patient, balance);
        entitlement.adjustUsed(-1);
        entitlements.save(entitlement);
        return usage;
    }

    /** The patient's entitlement row, created from their current ledger balance when it does not exist yet. */
    private CourseEntitlement entitlementOf(Course course, Patient patient, long balanceBefore) {
        return entitlements.findById(new CourseEntitlementId(course.getId(), patient.getId()))
                .orElseGet(() -> new CourseEntitlement(course, patient, (int) balanceBefore));
    }

    /** Locked rate if Monthly Closing has run; otherwise a provisional estimate (see class Javadoc). */
    private BigDecimal commissionAllocationPerVisit(Course course) {
        if (course.getCommissionAllocationPerVisit() != null) {
            return course.getCommissionAllocationPerVisit();
        }
        if (course.getProvisionalCommissionRate() == null) {
            throw new IllegalStateException("คอร์สนี้ยังไม่มี Commission Rate ให้คำนวณ");
        }
        BigDecimal provisionalPool = course.getCoursePrice().multiply(course.getProvisionalCommissionRate());
        return provisionalPool.divide(BigDecimal.valueOf(course.getTotalVisits()), 2, RoundingMode.HALF_UP);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
