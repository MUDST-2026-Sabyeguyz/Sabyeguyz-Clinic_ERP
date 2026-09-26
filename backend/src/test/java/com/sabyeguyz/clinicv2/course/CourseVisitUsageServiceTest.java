package com.sabyeguyz.clinicv2.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.commission.CommissionAllocationLedgerRepository;
import com.sabyeguyz.clinicv2.commission.TreatmentFeeRule;
import com.sabyeguyz.clinicv2.commission.TreatmentFeeRuleResolver;
import com.sabyeguyz.clinicv2.enums.CalculationBase;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Numbers mirror Commission_System_Requirement_Dev §29 Acceptance Criteria
 * (Case 3, 9, 10, 11) and §6 (Commission Release).
 */
@ExtendWith(MockitoExtension.class)
class CourseVisitUsageServiceTest {
    @Mock CourseVisitUsageRepository usages;
    @Mock CourseLedgerRepository ledger;
    @Mock CourseRepository courses;
    @Mock CommissionAllocationLedgerRepository allocationLedger;
    @Mock TreatmentFeeRuleResolver treatmentFeeRuleResolver;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private CourseVisitUsageService service() {
        return new CourseVisitUsageService(usages, ledger, courses, allocationLedger, treatmentFeeRuleResolver);
    }

    /** Locked Course: price 10,000 / 10 visits / rate 7% -> pool 700, 70 per visit (Commission doc §5 example). */
    private static Course lockedCourse() {
        Course course = new Course();
        setId(course, 1L);
        course.setCoursePrice(new BigDecimal("10000.00"));
        course.setTotalVisits(10);
        course.setRemainingVisits(10);
        course.setVisitsUsed(0);
        course.setExpiryDate(LocalDate.of(2026, 12, 31));
        course.setCourseStatus(CourseStatus.Active);
        course.setCommissionAllocationPerVisit(new BigDecimal("70.00"));
        course.setTotalCourseCommissionPool(new BigDecimal("700.00"));
        course.setGrossCommissionAllocatedTotal(BigDecimal.ZERO);
        course.setOwnerNetCommissionReleasedTotal(BigDecimal.ZERO);
        course.setSubstituteTreatmentFeeTotal(BigDecimal.ZERO);
        course.setCommissionOutstanding(new BigDecimal("700.00"));
        return course;
    }

    private void stubPatientBalance(Course course, Patient patient, long balance) {
        when(ledger.sumQtyChangeByCourseAndPatient(course.getId(), patient.getId())).thenReturn(balance);
    }

    @Test
    void caseOwnerTreatingThemselvesGetsTheFullAllocation() {
        Course course = lockedCourse();
        Staff owner = new Staff();
        setId(owner, 10L);
        course.setCaseOwnerStaff(owner);
        Patient patient = new Patient();
        setId(patient, 100L);
        stubPatientBalance(course, patient, 10);
        when(usages.save(any())).thenAnswer(i -> i.getArgument(0));
        when(courses.save(any())).thenAnswer(i -> i.getArgument(0));

        CourseVisitUsage usage = service().use(course, null, patient, owner, LocalDate.of(2026, 9, 1), 1L);

        assertThat(usage.getGrossCommissionAllocation()).isEqualByComparingTo("70.00");
        assertThat(usage.getSubstituteTreatmentFeeAmount()).isEqualByComparingTo("0");
        assertThat(usage.getOwnerNetCommission()).isEqualByComparingTo("70.00");
        assertThat(course.getRemainingVisits()).isEqualTo(9);
        assertThat(course.getVisitsUsed()).isEqualTo(1);
    }

    @Test
    void substituteFixedFeeMatchesCase3And9() {
        Course course = lockedCourse();
        Staff owner = new Staff();
        setId(owner, 10L);
        Staff substitute = new Staff();
        setId(substitute, 11L);
        course.setCaseOwnerStaff(owner);
        Patient patient = new Patient();
        setId(patient, 100L);
        stubPatientBalance(course, patient, 10);

        TreatmentFeeRule rule = new TreatmentFeeRule();
        rule.setFeeType(FeeType.Fixed);
        rule.setFeeValue(new BigDecimal("45"));
        when(treatmentFeeRuleResolver.resolve(substitute, LocalDate.of(2026, 9, 1))).thenReturn(Optional.of(rule));
        when(treatmentFeeRuleResolver.calculateFee(rule, course)).thenReturn(new BigDecimal("45.00"));
        when(usages.save(any())).thenAnswer(i -> i.getArgument(0));
        when(courses.save(any())).thenAnswer(i -> i.getArgument(0));

        CourseVisitUsage usage = service().use(course, null, patient, substitute, LocalDate.of(2026, 9, 1), 1L);

        assertThat(usage.getGrossCommissionAllocation()).isEqualByComparingTo("70.00");
        assertThat(usage.getSubstituteTreatmentFeeAmount()).isEqualByComparingTo("45.00");
        assertThat(usage.getOwnerNetCommission()).isEqualByComparingTo("25.00");
    }

    @Test
    void substitutePercentageFeeMatchesCase10() {
        Course course = lockedCourse();
        Staff owner = new Staff();
        setId(owner, 10L);
        Staff substitute = new Staff();
        setId(substitute, 11L);
        course.setCaseOwnerStaff(owner);
        Patient patient = new Patient();
        setId(patient, 100L);
        stubPatientBalance(course, patient, 10);

        TreatmentFeeRule rule = new TreatmentFeeRule();
        rule.setFeeType(FeeType.Percentage);
        rule.setFeeValue(new BigDecimal("0.03"));
        rule.setPercentageBase(CalculationBase.CourseValuePerVisit);
        when(treatmentFeeRuleResolver.resolve(substitute, LocalDate.of(2026, 9, 1))).thenReturn(Optional.of(rule));
        when(treatmentFeeRuleResolver.calculateFee(rule, course)).thenReturn(new BigDecimal("30.00"));
        when(usages.save(any())).thenAnswer(i -> i.getArgument(0));
        when(courses.save(any())).thenAnswer(i -> i.getArgument(0));

        CourseVisitUsage usage = service().use(course, null, patient, substitute, LocalDate.of(2026, 9, 1), 1L);

        assertThat(usage.getSubstituteTreatmentFeeAmount()).isEqualByComparingTo("30.00");
        assertThat(usage.getOwnerNetCommission()).isEqualByComparingTo("40.00");
    }

    @Test
    void feeExceedingAllocationIsCappedAndOwnerNetNeverGoesNegative() {
        Course course = lockedCourse();
        course.setCommissionAllocationPerVisit(new BigDecimal("40.00"));
        Staff owner = new Staff();
        setId(owner, 10L);
        Staff substitute = new Staff();
        setId(substitute, 11L);
        course.setCaseOwnerStaff(owner);
        Patient patient = new Patient();
        setId(patient, 100L);
        stubPatientBalance(course, patient, 10);

        TreatmentFeeRule rule = new TreatmentFeeRule();
        rule.setFeeType(FeeType.Fixed);
        rule.setFeeValue(new BigDecimal("60"));
        when(treatmentFeeRuleResolver.resolve(substitute, LocalDate.of(2026, 9, 1))).thenReturn(Optional.of(rule));
        when(treatmentFeeRuleResolver.calculateFee(rule, course)).thenReturn(new BigDecimal("60.00"));
        when(usages.save(any())).thenAnswer(i -> i.getArgument(0));
        when(courses.save(any())).thenAnswer(i -> i.getArgument(0));

        CourseVisitUsage usage = service().use(course, null, patient, substitute, LocalDate.of(2026, 9, 1), 1L);

        assertThat(usage.getSubstituteTreatmentFeeAmount()).isEqualByComparingTo("40.00");
        assertThat(usage.getOwnerNetCommission()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsUsageOnAnExpiredCourse() {
        Course course = lockedCourse();
        course.setExpiryDate(LocalDate.of(2020, 1, 1));
        Patient patient = new Patient();
        setId(patient, 100L);

        assertThatThrownBy(() -> service().use(course, null, patient, new Staff(), LocalDate.of(2026, 9, 1), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("หมดอายุ");
    }

    @Test
    void rejectsUsageWhenCourseHasNoRemainingVisits() {
        Course course = lockedCourse();
        course.setRemainingVisits(0);
        Patient patient = new Patient();
        setId(patient, 100L);

        assertThatThrownBy(() -> service().use(course, null, patient, new Staff(), LocalDate.of(2026, 9, 1), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUsageWhenThisPatientHasNoBalanceEvenIfCourseDoes() {
        Course course = lockedCourse();
        Patient patient = new Patient();
        setId(patient, 100L);
        stubPatientBalance(course, patient, 0);

        assertThatThrownBy(() -> service().use(course, null, patient, new Staff(), LocalDate.of(2026, 9, 1), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
