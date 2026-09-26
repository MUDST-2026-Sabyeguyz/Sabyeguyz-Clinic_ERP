package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.CommissionStatus;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly Closing (Commission_System_Requirement_Dev §10-12, §24, §30).
 * Freezes the seller's Commission Rate for the month and stamps it onto
 * every Course they sold that month — the "Course must remember its own
 * Tier forever" rule (§4).
 *
 * <p>Known gap, flagged for the team: if any Course from this month already
 * had visits used before closing ("Buy &amp; Use Same Day"), those
 * {@code Course_visit_usage}/{@code Commission_allocation_ledger} rows were
 * computed from the provisional per-visit amount, which can differ from the
 * now-locked one. This method does not retroactively adjust those rows —
 * the requirement doc does not specify how that reconciliation should
 * work.</p>
 */
@org.springframework.stereotype.Service
public class MonthlyCommissionClosingService {
    private final MonthlyCommissionClosingRepository closings;
    private final CourseRepository courses;
    private final CommissionSchemeResolver schemeResolver;
    private final CommissionTierResolver tierResolver;

    public MonthlyCommissionClosingService(MonthlyCommissionClosingRepository closings, CourseRepository courses,
                                            CommissionSchemeResolver schemeResolver, CommissionTierResolver tierResolver) {
        this.closings = closings;
        this.courses = courses;
        this.schemeResolver = schemeResolver;
        this.tierResolver = tierResolver;
    }

    @Transactional
    public MonthlyCommissionClosing closeMonth(String saleMonth, Staff staff, Permission permission, Long actorId) {
        require(saleMonth != null && saleMonth.matches("\\d{4}-(0[1-9]|1[0-2])"), "closingMonth ต้องเป็นรูปแบบ yyyy-mm");
        require(staff != null, "ต้องระบุพนักงาน");
        if (closings.findByClosingMonthAndStaffId(saleMonth, staff.getId())
                .filter(MonthlyCommissionClosing::isClosingStatus).isPresent()) {
            throw new IllegalStateException("ปิดยอดเดือนนี้ของพนักงานคนนี้ไปแล้ว");
        }

        LocalDate effectiveDate = LocalDate.parse(saleMonth + "-01");
        BigDecimal monthlySales = courses.sumCoursePriceBySellerAndSaleMonth(staff.getId(), saleMonth);
        CommissionScheme scheme = schemeResolver.resolveEffective(effectiveDate);
        CommissionTier tier = tierResolver.resolve(scheme, monthlySales);

        MonthlyCommissionClosing closing = new MonthlyCommissionClosing();
        closing.setClosingMonth(saleMonth);
        closing.setPermission(permission);
        closing.setStaff(staff);
        closing.setMonthlyCourseSales(monthlySales);
        closing.setCommissionScheme(scheme);
        closing.setCommissionSchemeVersion(scheme.getSchemeVersion());
        closing.setCalculatedCommissionRate(tier.getCommissionRate());
        closing.setLockedCommissionRate(tier.getCommissionRate());
        closing.setClosingStatus(true);
        closing.setClosedAt(Instant.now());
        closing.setClosedBy(actorId);
        MonthlyCommissionClosing saved = closings.save(closing);

        for (Course course : courses.findBySellerStaffIdAndSaleMonth(staff.getId(), saleMonth)) {
            lockCourse(course, scheme, tier);
        }

        return saved;
    }

    private void lockCourse(Course course, CommissionScheme scheme, CommissionTier tier) {
        BigDecimal pool = course.getCoursePrice().multiply(tier.getCommissionRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal perVisit = pool.divide(BigDecimal.valueOf(course.getTotalVisits()), 2, RoundingMode.HALF_UP);

        course.setCommissionStatus(CommissionStatus.Locked);
        course.setCommissionScheme(scheme);
        course.setCommissionSchemeVersion(scheme.getSchemeVersion());
        course.setLockedCommissionRate(tier.getCommissionRate());
        course.setTotalCourseCommissionPool(pool);
        course.setCommissionAllocationPerVisit(perVisit);
        course.setCommissionGeneratedDate(LocalDate.now());
        course.setCommissionOutstanding(pool.subtract(course.getGrossCommissionAllocatedTotal()));
        courses.save(course);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
