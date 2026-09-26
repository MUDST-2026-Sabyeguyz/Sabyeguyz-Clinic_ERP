package com.sabyeguyz.clinicv2.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.role.Permission;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommissionRateOverrideServiceTest {
    @Mock CommissionRateOverrideAuditRepository audits;
    @Mock CourseRepository courses;

    private CommissionRateOverrideService service() {
        return new CommissionRateOverrideService(audits, courses);
    }

    private static Course lockedCourse() {
        Course course = new Course();
        course.setCoursePrice(new BigDecimal("10000.00"));
        course.setTotalVisits(10);
        course.setLockedCommissionRate(new BigDecimal("0.07"));
        course.setGrossCommissionAllocatedTotal(new BigDecimal("210.00"));
        return course;
    }

    @Test
    void recordsTheAuditAndAppliesTheNewRate() {
        Course course = lockedCourse();
        when(audits.save(any())).thenAnswer(i -> i.getArgument(0));

        CommissionRateOverrideAudit audit = service().override(course, new BigDecimal("0.10"), new Permission(),
                "แก้ตามคำสั่งผู้บริหาร", 1L);

        assertThat(audit.getOldRate()).isEqualByComparingTo("0.07");
        assertThat(audit.getNewRate()).isEqualByComparingTo("0.10");
        assertThat(course.getLockedCommissionRate()).isEqualByComparingTo("0.10");
        assertThat(course.getTotalCourseCommissionPool()).isEqualByComparingTo("1000.00");
        assertThat(course.getCommissionOutstanding()).isEqualByComparingTo("790.00");
    }

    @Test
    void requiresAReason() {
        Course course = lockedCourse();
        assertThatThrownBy(() -> service().override(course, new BigDecimal("0.10"), new Permission(), " ", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverridingACourseThatWasNeverLocked() {
        Course course = new Course();
        course.setCoursePrice(new BigDecimal("10000.00"));
        course.setTotalVisits(10);
        assertThatThrownBy(() -> service().override(course, new BigDecimal("0.10"), new Permission(), "เหตุผล", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
