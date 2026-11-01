package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.commission.CourseCommissionService;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.sale.TransactionCancellation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    private final CourseRefundItemRepository refundItems;
    private final EntityManager entityManager;

    public CourseRightsService(
            CourseRepository courses,
            CourseEntitlementRepository entitlements,
            CourseLedgerRepository ledger,
            CourseCommissionService courseCommission,
            CourseRefundItemRepository refundItems,
            EntityManager entityManager) {
        this.courses = courses;
        this.entitlements = entitlements;
        this.ledger = ledger;
        this.courseCommission = courseCommission;
        this.refundItems = refundItems;
        this.entityManager = entityManager;
    }

    private record LockedCancellation(Course course, CourseRefundItem main) {}

    /** Always locks the course before the shared main item and reloads cached state. */
    private LockedCancellation lockCancellation(CourseRefundItem item) {
        require(item != null && item.getId() != null, "ต้องระบุรายการคืนเงินที่บันทึกแล้ว");
        require(item.getCourse() != null && item.getCourse().getId() != null,
                "รายการคืนเงินต้องระบุคอร์ส");
        Long courseId = item.getCourse().getId();
        Long mainId = item.getRightsCancellationItem() == null
                ? item.getId() : item.getRightsCancellationItem().getId();
        require(mainId != null, "ต้องระบุรายการหลักที่บันทึกแล้ว");
        Course course = lockedCourse(courseId);
        entityManager.refresh(course, LockModeType.PESSIMISTIC_WRITE);
        CourseRefundItem main = refundItems.findMainByIdForUpdate(mainId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ไม่พบรายการหลักของเหตุการณ์ยกเลิกสิทธิ์"));
        entityManager.refresh(main, LockModeType.PESSIMISTIC_WRITE);
        require(main.getRightsCancellationItem() == null, "ต้องอ้างรายการหลักโดยตรง");
        require(main.getCourse().getId().equals(courseId),
                "รายการร่วมต้องอยู่คอร์สเดียวกับรายการหลัก");
        return new LockedCancellation(course, main);
    }

    private boolean hasEntry(CourseRefundItem main, CourseLedgerEntryType type) {
        return ledger.existsByCourseRefundItemIdAndEntryType(main.getId(), type);
    }

    /** Checks availability only before the group's first actual cancellation. */
    private void requireAvailable(LockedCancellation locked) {
        CourseRefundItem main = locked.main();
        Integer count = main.getCancelledVisits();
        require(count != null && count >= 1,
                "รายการหลักต้องระบุจำนวนสิทธิ์ที่ยกเลิกอย่างน้อย 1 ครั้ง");
        int reserved = refundItems.pendingCancelledVisitsOfCourse(locked.course().getId());
        boolean groupPending = refundItems.findGroupRefundStatuses(main.getId())
                .contains(com.sabyeguyz.clinicv2.enums.RefundStatus.Pending);
        long available = (long) locked.course().getRemainingVisits() - reserved
                + (groupPending ? count : 0);
        require(count <= available, "สิทธิ์คงเหลือหลังหักสิทธิ์ที่จองไว้ไม่พอสำหรับการยกเลิก");
    }

    /** Revalidates a failed refund without cutting or reserving the same event twice. */
    @Transactional
    public void validateRefundRetry(CourseRefundItem item) {
        LockedCancellation locked = lockCancellation(item);
        require(!hasEntry(locked.main(), CourseLedgerEntryType.Reversal),
                "เหตุการณ์นี้กลับสิทธิ์แล้ว ต้องสร้างรายการหลักใหม่");
        if (!hasEntry(locked.main(), CourseLedgerEntryType.Cancellation)) {
            requireAvailable(locked);
        }
    }

    /** Cuts once through the main item, regardless of which refund completes first. */
    @Transactional
    public void cutForRefund(CourseRefundItem item, Long actorId) {
        LockedCancellation locked = lockCancellation(item);
        require(!hasEntry(locked.main(), CourseLedgerEntryType.Reversal),
                "เหตุการณ์นี้กลับสิทธิ์แล้ว ต้องสร้างรายการหลักใหม่");
        if (hasEntry(locked.main(), CourseLedgerEntryType.Cancellation)) return;
        requireAvailable(locked);
        cut(locked.course(), locked.main().getCancelledVisits(),
                locked.main(), null, "คืนเงินคอร์ส", actorId);
    }

    /** Reverses only real cancellations after every refund in this group is voided. */
    @Transactional
    public void restoreForVoidedGroup(CourseRefundItem item, Long actorId) {
        LockedCancellation locked = lockCancellation(item);
        Course course = locked.course();
        CourseRefundItem main = locked.main();
        var statuses = refundItems.findGroupRefundStatuses(main.getId());
        if (statuses.isEmpty() || statuses.stream().anyMatch(
                status -> status != com.sabyeguyz.clinicv2.enums.RefundStatus.Voided)) return;
        List<CourseLedger> originals = ledger.findByCourseRefundItemIdAndEntryTypeOrderByIdAsc(
                main.getId(), CourseLedgerEntryType.Cancellation);
        if (originals.isEmpty()) return;
        require(actorId != null, "ต้องระบุผู้ดำเนินการกลับสิทธิ์");
        long cancelled = originals.stream().mapToLong(row -> -(long) row.getQtyChange()).sum();
        require(main.getCancelledVisits() != null && cancelled == main.getCancelledVisits(),
                "ยอดตัดสิทธิ์ใน ledger ไม่ตรงกับรายการหลัก");
        var holders = entitlements.findByCourseId(course.getId());
        holders.forEach(holder -> entityManager.refresh(holder, LockModeType.PESSIMISTIC_WRITE));
        int restored = 0;
        for (CourseLedger original : originals) {
            if (ledger.existsByReversesLedgerId(original.getId())) continue;
            require(original.getCourse().getId().equals(course.getId())
                            && original.getQtyChange() < 0,
                    "ต้นทางการกลับสิทธิ์ไม่ถูกต้อง");
            CourseEntitlement holder = holders.stream()
                    .filter(candidate -> candidate.getPatient().getId()
                            .equals(original.getPatient().getId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "ไม่พบผู้ถือสิทธิ์เดิมของรายการ Cancellation"));
            int count = Math.negateExact(original.getQtyChange());
            CourseLedger reversal = CourseLedgerEntries.reversalOf(
                    original, holder.getRemainingVisits(), "ยกเลิกกลุ่มคำขอคืนเงิน", actorId);
            reversal.setCourseRefundItem(main);
            holder.adjustAllocated(count);
            entitlements.save(holder);
            ledger.save(reversal);
            restored = Math.addExact(restored, count);
        }
        if (restored == 0) return;
        int newRemaining = Math.addExact(course.getRemainingVisits(), restored);
        require((long) course.getVisitsUsed() + newRemaining <= course.getTotalVisits(),
                "การกลับสิทธิ์ทำให้จำนวนครั้งเกินจำนวนรวมของคอร์ส");
        course.setRemainingVisits(newRemaining);
        // A separate sale cancellation must not be reopened by a refund reversal.
        if (newRemaining > 0 && course.getCourseStatus() != CourseStatus.Cancelled) {
            course.setCourseStatus(CourseStatus.Active);
        }
        course.setCourseUpdateAt(Instant.now());
        course.setCourseUpdateBy(actorId);
        courseCommission.refreshTotals(course);
        courses.save(course);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
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
