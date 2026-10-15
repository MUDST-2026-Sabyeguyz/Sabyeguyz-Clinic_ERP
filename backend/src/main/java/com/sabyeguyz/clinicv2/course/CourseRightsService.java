package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.commission.CourseCommissionService;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.sale.TransactionCancellation;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stops unused rights of a course because of a refund or a cancellation (Data Dictionary: Course_ledger.Cancellation).
 * Only the rights not yet used are cut, one Cancellation ledger row per holder, all in the caller's transaction; the
 * treatment fees and commissions already earned are never touched. The cut is applied once per source: a retry of the
 * same refund item or cancellation does nothing.
 */
@Service
public class CourseRightsService {
    private final CourseRepository courses;
    private final CourseEntitlementRepository entitlements;
    private final CourseLedgerRepository ledger;
    private final CourseCommissionService courseCommission;

    public CourseRightsService(CourseRepository courses, CourseEntitlementRepository entitlements,
                                CourseLedgerRepository ledger, CourseCommissionService courseCommission) {
        this.courses = courses;
        this.entitlements = entitlements;
        this.ledger = ledger;
        this.courseCommission = courseCommission;
    }

    /** Cuts {@code count} unused rights because of a refund item. */
    @Transactional
    public void cutForRefund(CourseRefundItem item, Long actorId) {
        if (ledger.existsByCourseRefundItemId(item.getId())) {
            return;
        }
        Course course = lockedCourse(item.getCourse().getId());
        cut(course, item.getCancelledVisits(), item, null, "คืนเงินคอร์ส", actorId);
    }

    /** Cuts every unused right because the sale of the course was cancelled. */
    @Transactional
    public void cutAllForCancellation(Long courseId, TransactionCancellation cancellation, String reason, Long actorId) {
        if (ledger.existsByCancellationIdAndCourseId(cancellation.getId(), courseId)) {
            return;
        }
        Course course = lockedCourse(courseId);
        if (course.getRemainingVisits() > 0) {
            cut(course, course.getRemainingVisits(), null, cancellation, reason, actorId);
        } else if (course.getCourseStatus() == CourseStatus.Active) {
            closeCourse(course, CourseStatus.Cancelled, actorId);
        }
    }

    private Course lockedCourse(Long courseId) {
        return courses.findByIdForUpdate(courseId).orElseThrow(() -> new IllegalStateException("ไม่พบคอร์ส"));
    }

    private void cut(Course course, int count, CourseRefundItem item, TransactionCancellation cancellation,
                     String remark, Long actorId) {
        if (count < 1 || count > course.getRemainingVisits()) {
            throw new IllegalStateException("สิทธิ์คงเหลือของคอร์สไม่พอสำหรับการยกเลิก " + count + " ครั้ง");
        }
        List<CourseEntitlement> holders = entitlements.findByCourseId(course.getId());
        Long purchaserId = course.getPurchaserPatient().getId();
        holders.sort(Comparator.comparing((CourseEntitlement e) -> !e.getPatient().getId().equals(purchaserId))
                .thenComparing(e -> e.getPatient().getId()));
        int left = count;
        for (CourseEntitlement holder : holders) {
            if (left == 0) break;
            int take = Math.min(left, holder.getRemainingVisits());
            if (take == 0) continue;
            holder.adjustAllocated(-take);
            entitlements.save(holder);
            CourseLedger entry = new CourseLedger();
            entry.setCourse(course);
            entry.setPatient(holder.getPatient());
            entry.setEntryType(CourseLedgerEntryType.Cancellation);
            entry.setQtyChange(-take);
            entry.setBalanceAfter(holder.getRemainingVisits());
            entry.setCourseRefundItem(item);
            entry.setCancellation(cancellation);
            entry.setRemark(remark);
            entry.setLedgerCreateAt(Instant.now());
            entry.setLedgerCreateBy(actorId);
            entry.setEventAt(Instant.now());
            ledger.save(entry);
            left -= take;
        }
        if (left > 0) {
            throw new IllegalStateException("สิทธิ์คงเหลือรายคนไม่พอสำหรับการยกเลิก");
        }
        course.setRemainingVisits(course.getRemainingVisits() - count);
        if (course.getRemainingVisits() == 0) {
            closeCourse(course, item != null ? CourseStatus.Refunded : CourseStatus.Cancelled, actorId);
        } else {
            course.setCourseUpdateAt(Instant.now());
            course.setCourseUpdateBy(actorId);
            courseCommission.refreshTotals(course);
            courses.save(course);
        }
    }

    /** All rights are gone: the course is Cancelled/Refunded, and no commission is left to come from it. */
    private void closeCourse(Course course, CourseStatus status, Long actorId) {
        course.setCourseStatus(status);
        course.setCourseUpdateAt(Instant.now());
        course.setCourseUpdateBy(actorId);
        courseCommission.refreshTotals(course);
        courses.save(course);
    }
}
