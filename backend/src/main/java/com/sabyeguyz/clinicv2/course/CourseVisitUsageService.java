package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.commission.CommissionAllocationLedger;
import com.sabyeguyz.clinicv2.commission.CommissionAllocationLedgerRepository;
import com.sabyeguyz.clinicv2.commission.CourseCommissionService;
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
 * Uses one Course session (Data Dictionary: Course_visit_usage).
 *
 * <ul>
 *   <li>The session takes the lowest free position of the course (usage_sequence, all users together); the Owner
 *       commission of a position comes from the locked rate and the rounding rule of the course.</li>
 *   <li>The Owner PT is the course's seller. The Owner earns the commission of the position only when treating the
 *       session personally and before leaving; a substitute earns the full treatment fee of the rule in force that day,
 *       never limited by the commission, and the Owner then gets 0.</li>
 *   <li>While the sale month is not closed the rate is unknown: an Owner-treated session keeps NULL (not 0) as its
 *       commission and gets its allocation row when the month is closed ({@link CourseCommissionService#settle}).</li>
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
    private final CourseCommissionService courseCommission;
    private final CourseUsagePrintService prints;

    public CourseVisitUsageService(CourseVisitUsageRepository usages, CourseLedgerRepository ledger,
                                    CourseRepository courses, CommissionAllocationLedgerRepository allocationLedger,
                                    TreatmentFeeRuleResolver treatmentFeeRuleResolver,
                                    CourseEntitlementRepository entitlements,
                                    TransactionCancellationService cancellations,
                                    CourseCommissionService courseCommission, CourseUsagePrintService prints) {
        this.prints = prints;
        this.courseCommission = courseCommission;
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
        requireCanTreat(treatingStaff, visit, visitDate);
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

        Staff owner = course.getOwnerStaff();
        boolean substituted = !treatingStaff.getId().equals(owner.getId());

        BigDecimal treatmentFee = BigDecimal.ZERO;
        TreatmentFeeRule appliedRule = null;
        if (substituted) {
            appliedRule = treatmentFeeRuleResolver.resolve(visitDate)
                    .orElseThrow(() -> new IllegalStateException(
                            "ไม่พบ Treatment Fee Rule ที่ใช้ได้ในวันที่ " + visitDate));
            treatmentFee = treatmentFeeRuleResolver.calculateFee(appliedRule, course);
        }

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
        placeInCourse(usage, course);
        // NULL while the rate is unknown (sale month not closed); 0 when the Owner does not earn this session
        BigDecimal ownerNet = CourseCommissionService.ownerNetOf(course, usage.getUsageSequence(), treatingStaff, visitDate);
        usage.setCommissionAllocationPerVisit(CourseCommissionService.isRateKnown(course)
                ? CourseCommissionService.rateAt(course, usage.getUsageSequence()) : null);
        usage.setTreatmentFeeRule(appliedRule);
        usage.setSubstituteTreatmentFeeAmount(treatmentFee);
        usage.setOwnerNetCommission(ownerNet);
        usage.setUsageCreateAt(Instant.now());
        usage.setUsageCreateBy(actorId);
        CourseVisitUsage savedUsage = usages.save(usage);

        if (ownerNet != null) {
            courseCommission.recordAllocation(savedUsage, ownerNet, 1, null, actorId);
        }

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
        courseCommission.refreshTotals(course);
        courses.save(course);
        // a Completed session is printed on the course usage slip with the history of every user (Data Dictionary)
        prints.issue(savedUsage, actorId);

        return savedUsage;
    }

    /**
     * The PT who treats a session is chosen when the patient comes (not taken from a transfer): an active PT of the
     * branch where the service is given, who had not left by the day, and the same PT for the visit and the usage.
     */
    private static void requireCanTreat(Staff treatingStaff, PatientVisit visit, LocalDate visitDate) {
        boolean workingThatDay = treatingStaff.getTerminationDate() != null
                ? !visitDate.isAfter(treatingStaff.getTerminationDate()) // a PT who is leaving still works until the last day
                : treatingStaff.isStaffStatus();
        require(workingThatDay, treatingStaff.getTerminationDate() != null
                ? "ผู้รักษาลาออกก่อนวันที่ใช้บริการแล้ว" : "ผู้รักษาไม่ได้อยู่ในสถานะใช้งาน");
        if (visit.getBranch() != null && treatingStaff.getBranch() != null
                && !visit.getBranch().getId().equals(treatingStaff.getBranch().getId())) {
            throw new IllegalArgumentException("ผู้รักษาต้องอยู่สาขาเดียวกับสาขาที่ให้บริการ");
        }
        if (visit.getTreatingStaff() != null && !visit.getTreatingStaff().getId().equals(treatingStaff.getId())) {
            throw new IllegalArgumentException("ผู้รักษาของการใช้สิทธิ์ต้องเป็นคนเดียวกับผู้รักษาของ Visit");
        }
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
        if (position > course.getTotalVisits()) {
            throw new IllegalStateException("ลำดับสิทธิ์เต็มจำนวนครั้งของคอร์สแล้ว");
        }
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

        // A session whose Owner commission is still unknown has no allocation yet, so there is nothing to reverse.
        courseCommission.currentAllocation(usage).ifPresent(original -> {
            CourseCommissionService.requireNotPaid(original);
            allocationLedger.save(courseCommission.reversalOf(original, reason, actorId));
        });

        usage.setUsageStatus(UsageStatus.Reversed);
        usage.setUsageUpdateAt(Instant.now());
        usage.setUsageUpdateBy(actorId);
        usages.save(usage);

        course.setVisitsUsed(course.getVisitsUsed() - 1);
        course.setRemainingVisits(course.getRemainingVisits() + 1);
        courseCommission.refreshTotals(course);
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
