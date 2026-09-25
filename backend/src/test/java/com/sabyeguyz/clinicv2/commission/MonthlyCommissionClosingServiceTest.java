package com.sabyeguyz.clinicv2.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Mirrors Commission_System_Requirement_Dev §11's worked example: หมอกิ๊ฟ, August, 75,000 THB -> 7%. */
@ExtendWith(MockitoExtension.class)
class MonthlyCommissionClosingServiceTest {
    @Mock MonthlyCommissionClosingRepository closings;
    @Mock CourseRepository courses;
    @Mock CommissionSchemeResolver schemeResolver;
    @Mock CommissionTierResolver tierResolver;

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private MonthlyCommissionClosingService service() {
        return new MonthlyCommissionClosingService(closings, courses, schemeResolver, tierResolver);
    }

    @Test
    void locksTheRateAndStampsItOntoEveryCourseSoldThatMonth() {
        Staff seller = new Staff();
        setId(seller, 1L);
        when(closings.findByClosingMonthAndStaffId("2026-08", 1L)).thenReturn(Optional.empty());
        when(courses.sumCoursePriceBySellerAndSaleMonth(1L, "2026-08")).thenReturn(new BigDecimal("75000"));
        CommissionScheme scheme = new CommissionScheme();
        scheme.setSchemeVersion(1);
        when(schemeResolver.resolveEffective(LocalDate.of(2026, 8, 1))).thenReturn(scheme);
        CommissionTier tier = new CommissionTier();
        tier.setCommissionRate(new BigDecimal("0.07"));
        when(tierResolver.resolve(scheme, new BigDecimal("75000"))).thenReturn(tier);
        when(closings.save(any())).thenAnswer(i -> i.getArgument(0));

        Course course = new Course();
        course.setCoursePrice(new BigDecimal("10000.00"));
        course.setTotalVisits(10);
        course.setGrossCommissionAllocatedTotal(BigDecimal.ZERO);
        when(courses.findBySellerStaffIdAndSaleMonth(1L, "2026-08")).thenReturn(List.of(course));
        when(courses.save(any())).thenAnswer(i -> i.getArgument(0));

        MonthlyCommissionClosing closing = service().closeMonth("2026-08", seller, null, 9L);

        assertThat(closing.getLockedCommissionRate()).isEqualByComparingTo("0.07");
        assertThat(closing.isClosingStatus()).isTrue();
        assertThat(course.getCommissionStatus()).isEqualTo(CommissionStatus.Locked);
        assertThat(course.getLockedCommissionRate()).isEqualByComparingTo("0.07");
        assertThat(course.getTotalCourseCommissionPool()).isEqualByComparingTo("700.00");
        assertThat(course.getCommissionAllocationPerVisit()).isEqualByComparingTo("70.00");
    }

    @Test
    void rejectsClosingTheSameStaffAndMonthTwice() {
        Staff seller = new Staff();
        setId(seller, 1L);
        MonthlyCommissionClosing existing = new MonthlyCommissionClosing();
        existing.setClosingStatus(true);
        when(closings.findByClosingMonthAndStaffId("2026-08", 1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().closeMonth("2026-08", seller, null, 9L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAMalformedMonth() {
        assertThatThrownBy(() -> service().closeMonth("August-2026", new Staff(), null, 9L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
