package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.role.Permission;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin Override for a Course's locked rate after its month is Closed
 * (Commission_System_Requirement_Dev §12). Every override is audited —
 * old rate, new rate, who, when, why — and never applied silently.
 */
@Service
public class CommissionRateOverrideService {
    private final CommissionRateOverrideAuditRepository audits;
    private final CourseRepository courses;
    private final MonthlyCommissionClosingRepository closings;
    private final CourseCommissionService courseCommission;

    public CommissionRateOverrideService(CommissionRateOverrideAuditRepository audits, CourseRepository courses,
                                          MonthlyCommissionClosingRepository closings,
                                          CourseCommissionService courseCommission) {
        this.audits = audits;
        this.courses = courses;
        this.closings = closings;
        this.courseCommission = courseCommission;
    }

    @Transactional
    public CommissionRateOverrideAudit override(Course course, BigDecimal newRate, Permission permission,
                                                 String reason, Long actorId) {
        require(course != null, "ต้องระบุคอร์ส");
        require(newRate != null && newRate.signum() >= 0 && newRate.compareTo(BigDecimal.ONE) <= 0,
                "อัตราค่าคอมมิชชั่นต้องอยู่ระหว่าง 0 ถึง 1");
        require(reason != null && !reason.isBlank(), "ต้องระบุเหตุผลของการแก้ไข");
        require(course.getLockedCommissionRate() != null, "คอร์สนี้ยังไม่ถูก Lock จึงไม่ต้อง Override");
        boolean finalized = closings.findByClosingMonthAndStaffIdAndBranchId(
                        course.getSaleMonth(),
                        course.getSellerStaff().getId(),
                        course.getBranch().getId())
                .map(closing -> closing.getClosingStatus() == com.sabyeguyz.clinicv2.enums.ClosingStatus.Finalized).orElse(false);
        if (finalized) {
            throw new IllegalStateException("เดือนขายของคอร์สนี้ Finalize แล้ว ห้ามเปลี่ยนอัตรา ให้บันทึกเป็น Adjustment ในงวดถัดไป");
        }

        BigDecimal oldRate = course.getLockedCommissionRate();

        CommissionRateOverrideAudit audit = new CommissionRateOverrideAudit();
        audit.setPermission(permission);
        audit.setCourse(course);
        audit.setOldRate(oldRate);
        audit.setNewRate(newRate);
        audit.setChangedBy(actorId);
        audit.setChangedAt(Instant.now());
        audit.setReason(reason);
        CommissionRateOverrideAudit saved = audits.save(audit);

        course.setLockedCommissionRate(newRate);
        BigDecimal pool = course.getCoursePrice().multiply(newRate).setScale(2, java.math.RoundingMode.HALF_UP);
        course.setTotalCourseCommissionPool(pool);
        course.setCommissionAllocationPerVisit(CommissionAllocationCalculator.nominal(pool, course.getTotalVisits()));
        courses.save(course);
        // sessions already allocated at the old rate are reversed and allocated again at the new one (before payment)
        courseCommission.recalculate(course, "แก้อัตราคอมมิชชั่นของคอร์ส: " + reason.trim(), actorId);
        courseCommission.refreshTotals(course);
        courses.save(course);

        return saved;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
