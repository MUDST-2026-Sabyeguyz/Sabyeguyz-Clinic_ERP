package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.commission.CommissionAllocationLedger;
import com.sabyeguyz.clinicv2.commission.CommissionAllocationLedgerRepository;
import com.sabyeguyz.clinicv2.commission.TreatmentFeeRule;
import com.sabyeguyz.clinicv2.commission.TreatmentFeeRuleResolver;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.OverflowPolicy;
import com.sabyeguyz.clinicv2.patient.Patient;
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

    public CourseVisitUsageService(CourseVisitUsageRepository usages, CourseLedgerRepository ledger,
                                    CourseRepository courses, CommissionAllocationLedgerRepository allocationLedger,
                                    TreatmentFeeRuleResolver treatmentFeeRuleResolver) {
        this.usages = usages;
        this.ledger = ledger;
        this.courses = courses;
        this.allocationLedger = allocationLedger;
        this.treatmentFeeRuleResolver = treatmentFeeRuleResolver;
    }

    @Transactional
    public CourseVisitUsage use(Course course, PatientVisit visit, Patient usingPatient, Staff treatingStaff,
                                 LocalDate visitDate, Long actorId) {
        require(course != null, "ต้องระบุคอร์ส");
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
        usage.setVisitDate(visitDate);
        usage.setVisitQty(1);
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
        treatmentEntry.setReferenceType("VISIT");
        treatmentEntry.setReferenceId(visit == null ? null : visit.getId());
        treatmentEntry.setLedgerCreateAt(Instant.now());
        treatmentEntry.setLedgerCreateBy(actorId);
        ledger.save(treatmentEntry);

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
