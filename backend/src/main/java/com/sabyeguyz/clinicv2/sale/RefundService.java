package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.course.CourseRefundItem;
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

/**
 * Refund requests against a completed payment: Pending, then Completed (money handed back) or Failed, or Voided before
 * any money moved. Pending and Completed refunds together may never exceed the payment.
 *
 * <p>A refund of a receipt that sold courses names, per course, the amount refunded and the unused sessions to cancel
 * ({@link ItemRequest}); the items add up to the refund. A course that was partly used can be refunded for what is
 * left. The rights are cut when the refund is Completed, in the same transaction as the refund, once per item: the
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

    public RefundService(
            RefundTransactionRepository refunds, TransactionCancellationService cancellationService,
            CourseRepository courses, CourseRefundItemRepository refundItems, CourseRightsService rights,
            CoursePaymentSourceService paymentSources, PaymentLockService paymentLocks, RefundLockService refundLocks) {
        this.refunds = refunds;
        this.cancellationService = cancellationService;
        this.courses = courses;
        this.refundItems = refundItems;
        this.rights = rights;
        this.paymentSources = paymentSources;
        this.paymentLocks = paymentLocks;
        this.refundLocks = refundLocks;
    }

    /** One course covered by a refund: the manual amount for it and the unused sessions to cancel. */
    public record ItemRequest(Long courseId, BigDecimal refundAmount, int cancelledVisits) {}

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
        List<Course> itemCourses = checkItems(requested, receiptCourses, amount, payment);

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
            item.setCourse(itemCourses.get(i));
            item.setRefundAmount(requested.get(i).refundAmount().setScale(2, RoundingMode.HALF_UP));
            item.setCancelledVisits(requested.get(i).cancelledVisits());
            refundItems.save(item);
        }
        return saved;
    }

    /** Every item names a course of the receipt once, with a positive amount and sessions that can still be cancelled. */
    private List<Course> checkItems(List<ItemRequest> items, List<Long> receiptCourses, BigDecimal total, PaymentTransaction payment
    ) {
        List<Course> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (ItemRequest item : items) {
            require(item.courseId() != null && receiptCourses.contains(item.courseId()),
                    "คอร์สที่ระบุไม่ได้อยู่ในใบเสร็จของรายการชำระเงินนี้");
            require(seen.add(item.courseId()), "ระบุคอร์สเดียวกันซ้ำในการคืนเงินครั้งเดียว");
            require(item.refundAmount() != null && item.refundAmount().signum() > 0
                    && item.refundAmount().stripTrailingZeros().scale() <= 2, "ยอดคืนของแต่ละคอร์สต้องมากกว่า 0 และมีทศนิยมไม่เกิน 2 ตำแหน่ง");
            require(item.cancelledVisits() >= 1, "จำนวนสิทธิ์ที่ยกเลิกต้องไม่น้อยกว่า 1 ครั้ง");
            Course course = courses.findByIdForUpdate(item.courseId())
                    .orElseThrow(() -> new IllegalStateException("ไม่พบคอร์ส"));
            require(course.getRemainingVisits() - refundItems.pendingCancelledVisitsOfCourse(course.getId()) >= item.cancelledVisits(),
                    "สิทธิ์ที่ยังไม่ใช้ของคอร์ส " + course.getCourseNo() + " ไม่พอสำหรับการยกเลิก " + item.cancelledVisits() + " ครั้ง");
            // Limit refunds to this course's net amount from the original payment.
            BigDecimal paidForCourse = paymentSources.netAmountFor(payment.getReceipt().getId(), course.getId());
            BigDecimal alreadyRefundedForCourse = refundItems.refundedOrPendingAmountOfCoursePayment(course.getId(), payment.getId());
            require(
                    alreadyRefundedForCourse.add(item.refundAmount()).compareTo(paidForCourse) <= 0,
                    "ยอดคืนของคอร์ส " + course.getCourseNo() + " จากรายการชำระเงินนี้เกินยอดรับสุทธิ"
            );
            sum = sum.add(item.refundAmount());
            result.add(course);
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

    /** The money could not be returned; no rights were cut, so a new request can be made. */
    @Transactional
    public RefundTransaction fail(Long refundId) {
        RefundTransaction refund = pending(refundId);
        refund.setRefundStatus(RefundStatus.Failed);
        return refunds.save(refund);
    }

    /** The request is withdrawn before any money moved. */
    @Transactional
    public RefundTransaction voidRequest(Long refundId) {
        RefundTransaction refund = pending(refundId);
        refund.setRefundStatus(RefundStatus.Voided);
        return refunds.save(refund);
    }

    @Transactional(readOnly = true)
    public List<CourseRefundItem> itemsOf(Long refundId) {
        return refundItems.findByRefundIdOrderByIdAsc(refundId);
    }

    private RefundTransaction pending(Long refundId) {
        RefundTransaction refund = refundLocks.lockForUpdate(refundId);
        require(
                refund.getRefundStatus() == RefundStatus.Pending,
                "รายการคืนเงินนี้ไม่ได้อยู่ในสถานะรอดำเนินการ"
        );
        return refund;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
