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
import com.sabyeguyz.clinicv2.enums.OverflowPolicy;
import com.sabyeguyz.clinicv2.enums.UsageStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.sale.TransactionCancellationService;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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

        BigDecimal allocationPerVisit = commissionAllocationPerVisit(course);
        BigDecimal gross = allocationPerVisit;
        Staff caseOwner = course.getCaseOwnerStaff();
        boolean substituted = !treatingStaff.getId().equals(caseOwner.getId());

        BigDecimal treatmentFee = BigDecimal.ZERO;
        TreatmentFeeRule appliedRule = null;
        OverflowPolicy overflowPolicyUsed = null;
        if (substituted) {
            appliedRule = treatmentFeeRuleResolver.resolve(treatingStaff, visitDate)
                    .orElseThrow(() -> new IllegalStateException(
                            "ไม่พบ Treatment Fee Rule สำหรับผู้รักษาแทนในวันที่ " + visitDate));
            BigDecimal rawFee = treatmentFeeRuleResolver.calculateFee(appliedRule, course);
            if (rawFee.compareTo(gross) > 0) {
                // Default policy per §15.2: CAP_AT_COMMISSION.
                overflowPolicyUsed = OverflowPolicy.CapAtCommission;
                treatmentFee = gross;
            } else {
                treatmentFee = rawFee;
            }
        }
        BigDecimal ownerNet = gross.subtract(treatmentFee);

        CourseVisitUsage usage = new CourseVisitUsage();
        usage.setVisit(visit);
        usage.setCourse(course);
        usage.setPatient(usingPatient);
        usage.setTreatingStaff(treatingStaff);
        usage.setCaseOwnerStaff(caseOwner);
        usage.setVisitDate(visitDate.atStartOfDay());
        usage.setVisitQty(1);
        usage.setUsageStatus(UsageStatus.Completed);
        usage.setPrintRequired(true);
        usage.setCommissionAllocationPerVisit(allocationPerVisit);
        usage.setGrossCommissionAllocation(gross);
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
        allocation.setCaseOwnerStaff(caseOwner);
        allocation.setTreatingStaff(treatingStaff);
        allocation.setVisitDate(visitDate);
        allocation.setGrossCommissionAllocation(gross);
        allocation.setTreatmentFeeRule(appliedRule);
        allocation.setTreatmentFeeType(appliedRule == null ? null : appliedRule.getFeeType());
        allocation.setTreatmentFeeRateOrAmount(appliedRule == null ? null : appliedRule.getFeeValue());
        allocation.setTreatmentFeeCalculationBase(appliedRule == null || appliedRule.getPercentageBase() == null
                ? null : appliedRule.getPercentageBase().name());
        allocation.setTreatmentFeeAmount(treatmentFee);
        allocation.setOwnerNetCommission(ownerNet);
        allocation.setOverflowPolicyUsed(overflowPolicyUsed);
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
        course.setGrossCommissionAllocatedTotal(course.getGrossCommissionAllocatedTotal().add(gross));
        course.setOwnerNetCommissionReleasedTotal(course.getOwnerNetCommissionReleasedTotal().add(ownerNet));
        course.setSubstituteTreatmentFeeTotal(course.getSubstituteTreatmentFeeTotal().add(treatmentFee));
        if (course.getTotalCourseCommissionPool() != null) {
            course.setCommissionOutstanding(
                    course.getTotalCourseCommissionPool().subtract(course.getGrossCommissionAllocatedTotal()));
        }
        courses.save(course);

        return savedUsage;
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
        reversal.setAdjustmentReference(original);
        reversal.setReversalReason(reason.trim());
        reversal.setVisitUsage(usage);
        reversal.setCourse(course);
        reversal.setPatient(patient);
        reversal.setCaseOwnerStaff(original.getCaseOwnerStaff());
        reversal.setTreatingStaff(original.getTreatingStaff());
        reversal.setVisitDate(original.getVisitDate());
        reversal.setGrossCommissionAllocation(original.getGrossCommissionAllocation().negate());
        reversal.setTreatmentFeeRule(original.getTreatmentFeeRule());
        reversal.setTreatmentFeeType(original.getTreatmentFeeType());
        reversal.setTreatmentFeeRateOrAmount(original.getTreatmentFeeRateOrAmount());
        reversal.setTreatmentFeeCalculationBase(original.getTreatmentFeeCalculationBase());
        reversal.setTreatmentFeeAmount(original.getTreatmentFeeAmount().negate());
        reversal.setOwnerNetCommission(original.getOwnerNetCommission().negate());
        reversal.setCompanyTopUpAmount(original.getCompanyTopUpAmount() == null ? null
                : original.getCompanyTopUpAmount().negate());
        reversal.setOverflowPolicyUsed(original.getOverflowPolicyUsed());
        reversal.setAllocationCreateAt(Instant.now());
        reversal.setAllocationCreateBy(actorId);
        allocationLedger.save(reversal);

        usage.setUsageStatus(UsageStatus.Reversed);
        usage.setUsageUpdateAt(Instant.now());
        usage.setUsageUpdateBy(actorId);
        usages.save(usage);

        course.setVisitsUsed(course.getVisitsUsed() - 1);
        course.setRemainingVisits(course.getRemainingVisits() + 1);
        course.setGrossCommissionAllocatedTotal(
                course.getGrossCommissionAllocatedTotal().subtract(original.getGrossCommissionAllocation()));
        course.setOwnerNetCommissionReleasedTotal(
                course.getOwnerNetCommissionReleasedTotal().subtract(original.getOwnerNetCommission()));
        course.setSubstituteTreatmentFeeTotal(
                course.getSubstituteTreatmentFeeTotal().subtract(original.getTreatmentFeeAmount()));
        if (course.getTotalCourseCommissionPool() != null) {
            course.setCommissionOutstanding(
                    course.getTotalCourseCommissionPool().subtract(course.getGrossCommissionAllocatedTotal()));
        }
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
