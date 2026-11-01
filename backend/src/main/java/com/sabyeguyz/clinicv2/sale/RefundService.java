package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.course.CourseRefundItem;
import com.sabyeguyz.clinicv2.course.CourseLedgerRepository;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.course.CourseRefundItemRepository;
import com.sabyeguyz.clinicv2.course.CourseRightsService;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.enums.RefundStatus;
import com.sabyeguyz.clinicv2.role.Permission;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/**
 * Refund requests against a completed payment: Pending, then Completed (money handed back) or Failed, or Voided before
 * any money moved. Pending and Completed refunds together may never exceed the payment.
 *
 * <p>A refund of a receipt that sold courses names, per course, the amount refunded and the unused sessions to cancel
 * ({@link ItemRequest}); the items add up to the refund. A course that was partly used can be refunded for what is
 * left. The rights are cut when the refund is Completed, in the same transaction as the refund, once per shared cancellation event: the
 * treatment fees and commissions already earned are never taken back and the commission base is never recalculated.</p>
 */
@org.springframework.stereotype.Service
public class RefundService {
    private final RefundTransactionRepository refunds;
    private final TransactionCancellationService cancellationService;
    private final CourseRepository courses;
    private final CourseRefundItemRepository refundItems;
    private final CourseRightsService rights;
    private final CoursePaymentSourceService paymentSources;
    private final PaymentLockService paymentLocks;
    private final RefundLockService refundLocks;
    private final CourseLedgerRepository ledger;

    public RefundService(
            RefundTransactionRepository refunds, TransactionCancellationService cancellationService,
            CourseRepository courses, CourseRefundItemRepository refundItems, CourseRightsService rights,
            CoursePaymentSourceService paymentSources, PaymentLockService paymentLocks, RefundLockService refundLocks,
            CourseLedgerRepository ledger) {
        this.refunds = refunds;
        this.cancellationService = cancellationService;
        this.courses = courses;
        this.refundItems = refundItems;
        this.rights = rights;
        this.paymentSources = paymentSources;
        this.paymentLocks = paymentLocks;
        this.refundLocks = refundLocks;
        this.ledger = ledger;
    }

    /** One course covered by a refund: the manual amount for it and the unused sessions to cancel. */
    public record ItemRequest(
            Long courseId,
            BigDecimal refundAmount,
            Integer cancelledVisits,
            Long rightsCancellationItemId) {

        public ItemRequest(
                Long courseId,
                BigDecimal refundAmount,
                int cancelledVisits) {
            this(courseId, refundAmount, cancelledVisits, null);
        }
    }

    @Transactional
    public RefundTransaction request(PaymentTransaction payment, BigDecimal amount, PaymentMethod method,
                                      Permission permission, String reason, Long actorId, List<ItemRequest> items) {
        require(payment != null, "ต้องระบุรายการชำระเงิน");
        // Lock and reload before checking status or refund totals.
        payment = paymentLocks.lockForUpdate(payment.getId());
        require(payment.isPaymentStatus(), "รายการชำระเงินนี้ถูกยกเลิกแล้ว ไม่สามารถคืนเงินได้");
        require(method != null, "ต้องระบุช่องทางคืนเงิน");
        require(amount != null && amount.signum() > 0, "จำนวนเงินที่คืนต้องมากกว่า 0");
        List<Long> receiptCourses = courses.findLinkedCourseIdsByReceiptId(payment.getReceipt().getId());
        List<ItemRequest> requested = items == null ? List.of() : items;
        require(requested.stream().noneMatch(
                item -> item == null || item.courseId() == null
                ), "ต้องระบุคอร์สทุกรายการ"
        );
        // Acquire course locks in ascending course ID order.
        requested = requested.stream().sorted(Comparator.comparing(ItemRequest::courseId)).toList();
        require(receiptCourses.isEmpty() || !requested.isEmpty(),
                "ใบเสร็จนี้มีคอร์ส ต้องระบุคอร์สและจำนวนสิทธิ์ที่ยกเลิก (items)");
        require(requested.isEmpty() || !receiptCourses.isEmpty(), "ใบเสร็จนี้ไม่มีคอร์ส จึงไม่ต้องระบุ items");
        BigDecimal alreadyRefunded = refunds.refundedOrPendingAmountOf(payment.getId());
        require(alreadyRefunded.add(amount).compareTo(payment.getAmount()) <= 0,
                "จำนวนเงินที่คืนรวมกันต้องไม่เกินยอดที่ชำระ");
        List<CheckedItem> checkedItems = checkItems(requested, receiptCourses, amount, payment);

        TransactionCancellation cancellation = cancellationService.recordRefundCancellation(payment, permission,
                reason, actorId);
        RefundTransaction refund = new RefundTransaction();
        refund.setCancellation(cancellation);
        refund.setOriginalPayment(payment);
        refund.setRefundAmount(amount);
        refund.setPaymentMethod(method);
        refund.setRefundStatus(RefundStatus.Pending);
        refund.setRequestedAt(Instant.now());
        refund.setRequestedBy(actorId);
        RefundTransaction saved = refunds.save(refund);
        for (int i = 0; i < requested.size(); i++) {
            CourseRefundItem item = new CourseRefundItem();
            item.setRefund(saved);
            CheckedItem checked = checkedItems.get(i);
            item.setCourse(checked.course());
            item.setRefundAmount(requested.get(i).refundAmount().setScale(2, RoundingMode.HALF_UP));
            item.setCancelledVisits(requested.get(i).cancelledVisits());
            item.setRightsCancellationItem(checked.mainItem());
            refundItems.save(item);
        }
        return saved;
    }

    private record CheckedItem(Course course, CourseRefundItem mainItem) {}

    /** Validates the shared cancellation reference while the course is locked. */
    private CourseRefundItem checkCancellationReference(ItemRequest item, Course course) {
        Long mainItemId = item.rightsCancellationItemId();
        if (mainItemId == null) {
            require(item.cancelledVisits() != null && item.cancelledVisits() >= 1,
                    "รายการหลักต้องระบุจำนวนสิทธิ์ที่ยกเลิกอย่างน้อย 1 ครั้ง");
            return null;
        }
        require(mainItemId > 0, "รหัสรายการหลักต้องมากกว่า 0");
        require(item.cancelledVisits() == null,
                "รายการร่วมต้องไม่ระบุจำนวนสิทธิ์ที่ยกเลิก");
        CourseRefundItem main = refundItems.findMainByIdForUpdate(mainItemId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ไม่พบรายการหลักของเหตุการณ์ยกเลิกสิทธิ์"));
        require(main.getCourse().getId().equals(course.getId()),
                "รายการร่วมต้องอยู่คอร์สเดียวกับรายการหลัก");
        require(!ledger.existsByCourseRefundItemIdAndEntryType(
                        main.getId(), CourseLedgerEntryType.Reversal),
                "เหตุการณ์นี้กลับสิทธิ์แล้ว ต้องสร้างรายการหลักใหม่");
        return main;
    }

    /** Every item names a course of the receipt once, with a positive amount and sessions that can still be cancelled. */
    private List<CheckedItem> checkItems(List<ItemRequest> items, List<Long> receiptCourses, BigDecimal total, PaymentTransaction payment
    ) {
        List<CheckedItem> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (ItemRequest item : items) {
            require(item.courseId() != null && receiptCourses.contains(item.courseId()),
                    "คอร์สที่ระบุไม่ได้อยู่ในใบเสร็จของรายการชำระเงินนี้");
            require(seen.add(item.courseId()), "ระบุคอร์สเดียวกันซ้ำในการคืนเงินครั้งเดียว");
            require(item.refundAmount() != null && item.refundAmount().signum() > 0
                    && item.refundAmount().stripTrailingZeros().scale() <= 2, "ยอดคืนของแต่ละคอร์สต้องมากกว่า 0 และมีทศนิยมไม่เกิน 2 ตำแหน่ง");
            Course course = courses.findByIdForUpdate(item.courseId())
                    .orElseThrow(() -> new IllegalStateException("ไม่พบคอร์ส"));
            CourseRefundItem mainItem = checkCancellationReference(item, course);
            if (mainItem == null) {
                require(course.getRemainingVisits() - refundItems.pendingCancelledVisitsOfCourse(course.getId()) >= item.cancelledVisits(),
                        "สิทธิ์ที่ยังไม่ใช้ของคอร์ส " + course.getCourseNo() + " ไม่พอสำหรับการยกเลิก " + item.cancelledVisits() + " ครั้ง");
            }
            // Limit refunds to this course's net amount from the original payment.
            BigDecimal paidForCourse = paymentSources.netAmountFor(payment.getReceipt().getId(), course.getId());
            BigDecimal alreadyRefundedForCourse = refundItems.refundedOrPendingAmountOfCoursePayment(course.getId(), payment.getId());
            require(
                    alreadyRefundedForCourse.add(item.refundAmount()).compareTo(paidForCourse) <= 0,
                    "ยอดคืนของคอร์ส " + course.getCourseNo() + " จากรายการชำระเงินนี้เกินยอดรับสุทธิ"
            );
            sum = sum.add(item.refundAmount());
            result.add(new CheckedItem(course, mainItem));
        }
        require(items.isEmpty() || sum.compareTo(total) == 0, "ยอดคืนของแต่ละคอร์สต้องรวมกันเท่ากับยอดที่คืน");
        return result;
    }

    @Transactional
    public RefundTransaction complete(Long refundId, Long actorId) {
        RefundTransaction refund = pending(refundId);
        // Use the same course lock order as refund requests.
        List<CourseRefundItem> orderedItems = refundItems
                .findByRefundIdOrderByIdAsc(refund.getId())
                .stream()
                .sorted(Comparator.comparing(item -> item.getCourse().getId()))
                .toList();

        for (CourseRefundItem item : orderedItems) {
            rights.cutForRefund(item, actorId);
        }
        refund.setRefundStatus(RefundStatus.Completed);
        refund.setApprovedAt(Instant.now());
        refund.setApprovedBy(actorId);
        refund.setRefundedAt(Instant.now());
        refund.setRefundedBy(actorId);
        return refunds.save(refund);
    }

    /** A failed money return does not reverse rights already cut by the group. */
    @Transactional
    public RefundTransaction fail(Long refundId) {
        RefundTransaction refund = pending(refundId);
        refund.setRefundStatus(RefundStatus.Failed);
        return refunds.save(refund);
    }

    /** Retries the same failed refund after rechecking money and rights availability. */
    @Transactional
    public RefundTransaction retry(Long refundId) {
        RefundTransaction refund = lockedRefund(refundId);
        require(refund.getRefundStatus() == RefundStatus.Failed,
                "ลองใหม่ได้เฉพาะรายการคืนเงินที่ไม่สำเร็จ");
        PaymentTransaction payment = refund.getOriginalPayment();
        require(payment.isPaymentStatus(), "รายการชำระเงินนี้ถูกยกเลิกแล้ว ไม่สามารถคืนเงินได้");
        require(refunds.refundedOrPendingAmountOf(payment.getId())
                        .add(refund.getRefundAmount()).compareTo(payment.getAmount()) <= 0,
                "จำนวนเงินที่คืนรวมกันต้องไม่เกินยอดที่ชำระ");
        for (CourseRefundItem item : orderedItems(refund)) {
            BigDecimal paid = paymentSources.netAmountFor(
                    payment.getReceipt().getId(), item.getCourse().getId());
            require(refundItems.refundedOrPendingAmountOfCoursePayment(
                            item.getCourse().getId(), payment.getId())
                            .add(item.getRefundAmount()).compareTo(paid) <= 0,
                    "ยอดคืนของคอร์สจากรายการชำระเงินนี้เกินยอดรับสุทธิ");
            rights.validateRefundRetry(item);
        }
        refund.setRefundStatus(RefundStatus.Pending);
        return refunds.save(refund);
    }

    /** Voids one request and restores rights only when the whole group is voided. */
    @Transactional
    public RefundTransaction voidRequest(Long refundId, Long actorId) {
        RefundTransaction refund = lockedRefund(refundId);
        require(refund.getRefundStatus() == RefundStatus.Pending
                        || refund.getRefundStatus() == RefundStatus.Failed,
                "ยกเลิกได้เฉพาะคำขอคืนเงินที่รอดำเนินการหรือไม่สำเร็จ");
        refund.setRefundStatus(RefundStatus.Voided);
        refunds.saveAndFlush(refund);
        for (CourseRefundItem item : orderedItems(refund)) {
            rights.restoreForVoidedGroup(item, actorId);
        }
        return refund;
    }

    /** Keeps the original status stable while the controller records its audit transition. */
    @Transactional(propagation = Propagation.MANDATORY)
    public RefundStatus lockStatusForUpdate(Long refundId) {
        return lockedRefund(refundId).getRefundStatus();
    }

    private List<CourseRefundItem> orderedItems(RefundTransaction refund) {
        return refundItems.findByRefundIdOrderByIdAsc(refund.getId()).stream()
                .sorted(Comparator.comparing(item -> item.getCourse().getId())).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseRefundItem> itemsOf(Long refundId) {
        return refundItems.findByRefundIdOrderByIdAsc(refundId);
    }

    /** Locks cancellation groups in a consistent course and main-item order. */
    private void lockCancellationGroups(RefundTransaction refund) {
        List<CourseRefundItem> orderedItems = refundItems
                .findByRefundIdOrderByIdAsc(refund.getId())
                .stream()
                .sorted(Comparator
                        .comparing((CourseRefundItem item) ->
                                item.getCourse().getId())
                        .thenComparing(item ->
                                item.getRightsCancellationItem() == null
                                        ? item.getId()
                                        : item.getRightsCancellationItem().getId()))
                .toList();

        Set<Long> lockedCourses = new HashSet<>();
        Set<Long> lockedMainItems = new HashSet<>();

        for (CourseRefundItem item : orderedItems) {
            Long courseId = item.getCourse().getId();

            if (lockedCourses.add(courseId)) {
                courses.findByIdForUpdate(courseId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "ไม่พบคอร์สของรายการคืนเงิน"));
            }

            Long mainItemId = item.getRightsCancellationItem() == null
                    ? item.getId()
                    : item.getRightsCancellationItem().getId();

            if (lockedMainItems.add(mainItemId)) {
                CourseRefundItem main = refundItems
                        .findMainByIdForUpdate(mainItemId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "ไม่พบรายการหลักของเหตุการณ์ยกเลิกสิทธิ์"));

                require(
                        main.getCourse().getId().equals(courseId),
                        "รายการร่วมต้องอยู่คอร์สเดียวกับรายการหลัก");
            }
        }
    }

    private RefundTransaction lockedRefund(Long refundId) {
        RefundTransaction refund = refundLocks.lockForUpdate(refundId);
        lockCancellationGroups(refund);
        return refund;
    }

    private RefundTransaction pending(Long refundId) {
        RefundTransaction refund = lockedRefund(refundId);
        require(refund.getRefundStatus() == RefundStatus.Pending,
                "รายการคืนเงินนี้ไม่ได้อยู่ในสถานะรอดำเนินการ");
        return refund;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
