package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.role.Permission;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rejects direct manual rate overrides.
 * Rate-change audits are recorded during monthly recalculation.
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

    /** Rejects direct manual commission rate overrides. */
    @Transactional
    public CommissionRateOverrideAudit override(
            Course course,
            BigDecimal newRate,
            Permission permission,
            String reason,
            Long actorId) {
        throw new IllegalArgumentException(
                "ไม่อนุญาตให้กำหนดอัตราคอมมิชชันโดยตรง "
                        + "ให้แก้ข้อมูลต้นทางและคำนวณใหม่ก่อน Finalize");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
