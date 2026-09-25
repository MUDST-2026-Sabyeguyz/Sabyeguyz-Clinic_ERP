package com.sabyeguyz.clinicv2.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.enums.CalculationBase;
import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Numbers mirror Commission_System_Requirement_Dev §15's worked examples (Case 9/10). */
@ExtendWith(MockitoExtension.class)
class TreatmentFeeRuleResolverTest {
    @Mock TreatmentFeeRuleRepository repository;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Course courseOf(String price, int visits) {
        Course course = new Course();
        course.setCoursePrice(new BigDecimal(price));
        course.setTotalVisits(visits);
        return course;
    }

    @Test
    void fixedFeeMatchesTheDocExample() {
        TreatmentFeeRule rule = new TreatmentFeeRule();
        rule.setFeeType(FeeType.Fixed);
        rule.setFeeValue(new BigDecimal("45"));

        BigDecimal fee = new TreatmentFeeRuleResolver(repository).calculateFee(rule, courseOf("10000", 10));

        assertThat(fee).isEqualByComparingTo("45.00");
    }

    @Test
    void percentageFeeMatchesTheDocExample() {
        TreatmentFeeRule rule = new TreatmentFeeRule();
        rule.setFeeType(FeeType.Percentage);
        rule.setFeeValue(new BigDecimal("0.03"));
        rule.setPercentageBase(CalculationBase.CourseValuePerVisit);

        BigDecimal fee = new TreatmentFeeRuleResolver(repository).calculateFee(rule, courseOf("10000", 10));

        assertThat(fee).isEqualByComparingTo("30.00");
    }

    @Test
    void prefersAStaffSpecificRuleOverADefaultRule() {
        Staff staff = new Staff();
        setId(staff, 1L);
        TreatmentFeeRule generic = new TreatmentFeeRule();
        generic.setFeeType(FeeType.Fixed);
        generic.setFeeValue(new BigDecimal("40"));
        TreatmentFeeRule specific = new TreatmentFeeRule();
        specific.setStaff(staff);
        specific.setFeeType(FeeType.Fixed);
        specific.setFeeValue(new BigDecimal("45"));
        when(repository.findActiveOn(LocalDate.of(2026, 8, 20))).thenReturn(List.of(generic, specific));

        var resolved = new TreatmentFeeRuleResolver(repository).resolve(staff, LocalDate.of(2026, 8, 20));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getFeeValue()).isEqualByComparingTo("45");
    }
}
