package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.ClosingStatus;
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
 * <p>Sessions used before the close ("Buy &amp; Use Same Day") kept a NULL Owner commission, because the
 * rate was not known; closing the month settles them ({@link CourseCommissionService#settle}).</p>
 */
@org.springframework.stereotype.Service
public class MonthlyCommissionClosingService {
    private final MonthlyCommissionClosingRepository closings;
    private final CourseRepository courses;
    private final CommissionSchemeResolver schemeResolver;
    private final CommissionTierResolver tierResolver;
    private final MonthlyTierSalesService tierSales;
    private final CourseCommissionService courseCommission;

    public MonthlyCommissionClosingService(MonthlyCommissionClosingRepository closings, CourseRepository courses,
                                            CommissionSchemeResolver schemeResolver, CommissionTierResolver tierResolver,
                                            MonthlyTierSalesService tierSales, CourseCommissionService courseCommission) {
        this.courseCommission = courseCommission;
        this.tierSales = tierSales;
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
                .filter(MonthlyCommissionClosing::isClosed).isPresent()) {
            throw new IllegalStateException("ปิดยอดเดือนนี้ของพนักงานคนนี้ไปแล้ว");
        }

        LocalDate effectiveDate = LocalDate.parse(saleMonth + "-01");
        BigDecimal monthlySales = tierSales.netSales(staff.getId(), saleMonth);
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
        closing.setClosingStatus(ClosingStatus.Closed);
        closing.setClosedAt(Instant.now());
        closing.setClosedBy(actorId);
        MonthlyCommissionClosing saved = closings.save(closing);

        // each course is locked (row lock) like a usage does, so a session being recorded now is either settled here or
        // sees the locked rate itself
        for (Long courseId : courses.findIdsBySellerStaffIdAndSaleMonth(staff.getId(), saleMonth)) {
            Course course = courses.findByIdForUpdate(courseId).orElseThrow(() -> new IllegalStateException("ไม่พบคอร์ส"));
            lockCourse(course, scheme, tier, actorId);
        }

        return saved;
    }

    private void lockCourse(Course course, CommissionScheme scheme, CommissionTier tier, Long actorId) {
        BigDecimal pool = course.getCoursePrice().multiply(tier.getCommissionRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal perVisit = CommissionAllocationCalculator.nominal(pool, course.getTotalVisits());

        course.setCommissionStatus(CommissionStatus.Locked);
        course.setCommissionScheme(scheme);
        course.setCommissionSchemeVersion(scheme.getSchemeVersion());
        course.setLockedCommissionRate(tier.getCommissionRate());
        course.setTotalCourseCommissionPool(pool);
        course.setCommissionAllocationPerVisit(perVisit);
        course.setCommissionGeneratedDate(LocalDate.now());
        courses.save(course);
        // sessions used before the rate was known get their rate, Owner commission and allocation now
        courseCommission.settle(course, actorId);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
