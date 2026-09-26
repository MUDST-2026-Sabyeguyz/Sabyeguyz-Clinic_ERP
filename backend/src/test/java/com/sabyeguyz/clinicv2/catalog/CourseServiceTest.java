package com.sabyeguyz.clinicv2.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.commission.CommissionScheme;
import com.sabyeguyz.clinicv2.commission.CommissionSchemeResolver;
import com.sabyeguyz.clinicv2.commission.CommissionTier;
import com.sabyeguyz.clinicv2.commission.CommissionTierResolver;
import com.sabyeguyz.clinicv2.course.CourseLedgerRepository;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.enums.ServiceType;
import com.sabyeguyz.clinicv2.sale.Receipt;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Numbers mirror the Commission_System_Requirement_Dev §4 worked example (10,000 THB course, 7% tier). */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {
    @Mock CourseRepository courses;
    @Mock CourseLedgerRepository ledger;
    @Mock CommissionSchemeResolver schemeResolver;
    @Mock CommissionTierResolver tierResolver;

    private CourseService service() {
        return new CourseService(courses, ledger, schemeResolver, tierResolver);
    }

    private static Service courseTemplate() {
        Service template = new Service();
        template.setServiceType(ServiceType.Course);
        template.setServiceName("Routine Pain 10 Visits");
        template.setServicePrice(new BigDecimal("10000.00"));
        template.setDefaultTotalVisits(10);
        template.setDefaultBonusVisits(0);
        template.setDefaultValidityDays(90);
        return template;
    }

    @Test
    void sellsACourseWithAProvisionalTierRate() {
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-2206315");
        CommissionScheme scheme = new CommissionScheme();
        scheme.setSchemeVersion(1);
        CommissionTier tier = new CommissionTier();
        tier.setCommissionRate(new BigDecimal("0.07"));

        when(courses.countByReceiptId(any())).thenReturn(0L);
        when(courses.sumCoursePriceBySellerAndSaleMonth(any(), any())).thenReturn(BigDecimal.ZERO);
        when(schemeResolver.resolveEffective(any())).thenReturn(scheme);
        when(tierResolver.resolve(any(), any())).thenReturn(tier);
        when(courses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Course course = service().sell(receipt, courseTemplate(), new Staff(), null, null, null, null,
                LocalDate.of(2026, 8, 18), 1L);

        assertThat(course.getCourseNo()).isEqualTo("RE-2206315");
        assertThat(course.getCoursePrice()).isEqualByComparingTo("10000.00");
        assertThat(course.getTotalVisits()).isEqualTo(10);
        assertThat(course.getRemainingVisits()).isEqualTo(10);
        assertThat(course.getProvisionalCommissionRate()).isEqualByComparingTo("0.07");
        assertThat(course.getCommissionStatus()).isEqualTo(CommissionStatus.Provisional);
        assertThat(course.getCourseStatus()).isEqualTo(CourseStatus.Active);
        assertThat(course.getCaseOwnerStaff()).isSameAs(course.getSellerStaff());
        assertThat(course.getExpiryDate()).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void appendsSequenceSuffixWhenMultipleCoursesShareAReceipt() {
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-2206315");
        when(courses.countByReceiptId(any())).thenReturn(1L);
        when(courses.sumCoursePriceBySellerAndSaleMonth(any(), any())).thenReturn(BigDecimal.ZERO);
        when(schemeResolver.resolveEffective(any())).thenReturn(new CommissionScheme());
        when(tierResolver.resolve(any(), any())).thenReturn(new CommissionTier());
        when(courses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Course course = service().sell(receipt, courseTemplate(), new Staff(), null, null, null, null,
                LocalDate.of(2026, 8, 18), 1L);

        assertThat(course.getCourseNo()).isEqualTo("RE-2206315-02");
        assertThat(course.getCourseSequence()).isEqualTo(2);
    }

    @Test
    void remainingVisitsIncludesBonusButCommissionRateIgnoresIt() {
        Service template = courseTemplate();
        template.setDefaultBonusVisits(3);
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-3000001");
        CommissionTier tier = new CommissionTier();
        tier.setCommissionRate(new BigDecimal("0.07"));
        when(courses.countByReceiptId(any())).thenReturn(0L);
        when(courses.sumCoursePriceBySellerAndSaleMonth(any(), any())).thenReturn(BigDecimal.ZERO);
        when(schemeResolver.resolveEffective(any())).thenReturn(new CommissionScheme());
        when(tierResolver.resolve(any(), any())).thenReturn(tier);
        when(courses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Course course = service().sell(receipt, template, new Staff(), null, null, null, null,
                LocalDate.of(2026, 8, 18), 1L);

        assertThat(course.getTotalVisits()).isEqualTo(10);
        assertThat(course.getRemainingVisits()).isEqualTo(13);
    }

    @Test
    void rejectsACourseTemplateWithoutADefaultValidity() {
        Service template = courseTemplate();
        template.setDefaultValidityDays(null);
        Receipt receipt = new Receipt();
        receipt.setReceiptNo("RE-4000001");

        assertThatThrownBy(() -> service().sell(receipt, template, new Staff(), null, null, null, null,
                LocalDate.of(2026, 8, 18), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
