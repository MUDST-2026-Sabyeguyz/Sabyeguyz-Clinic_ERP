package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.enums.RefundStatus;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRefundItemRepository extends JpaRepository<CourseRefundItem, Long> {
    List<CourseRefundItem> findByRefundIdOrderByIdAsc(Long refundId);

    /** Locks the main item that identifies a shared cancellation event. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select i
        from CourseRefundItem i
        where i.id = :mainItemId
          and i.rightsCancellationItem is null
        """)
    Optional<CourseRefundItem> findMainByIdForUpdate(
            @Param("mainItemId") Long mainItemId);

    /** Reads refund statuses for the main item and its related items. */
    @Query("""
        select r.refundStatus
        from CourseRefundItem i
        join i.refund r
        left join i.rightsCancellationItem main
        where i.id = :mainItemId
           or main.id = :mainItemId
        order by i.id
        """)
    List<RefundStatus> findGroupRefundStatuses(
            @Param("mainItemId") Long mainItemId);

    /** What Pending and Completed refunds already took from a course (Failed and Voided free the amount again). */
    default BigDecimal refundedAmountOfCourse(Long courseId) {
        return sumRefundAmountOfCourse(courseId, List.of(RefundStatus.Pending, RefundStatus.Completed));
    }

    /** The status list is a parameter: a PostgreSQL enum literal in JPQL is cast to the wrong type name. */
    @Query("select coalesce(sum(i.refundAmount), 0) from CourseRefundItem i "
            + "where i.course.id = :courseId and i.refund.refundStatus in :statuses")
    BigDecimal sumRefundAmountOfCourse(@Param("courseId") Long courseId, @Param("statuses") List<RefundStatus> statuses);

    /** Counts pending cancellation groups whose rights have not been cut yet. */
    default int pendingCancelledVisitsOfCourse(Long courseId) {
        Long total = sumUnappliedCancelledVisitsOfCourse(
                courseId,
                List.of(RefundStatus.Pending),
                CourseLedgerEntryType.Cancellation);
        return total == null ? 0 : Math.toIntExact(total);
    }

    /** Counts each main item once, including pending related refunds. */
    @Query("""
        select coalesce(sum(main.cancelledVisits), 0)
        from CourseRefundItem main
        where main.course.id = :courseId
          and main.rightsCancellationItem is null
          and (
              main.refund.refundStatus in :statuses
              or exists (
                  select related.id
                  from CourseRefundItem related
                  where related.rightsCancellationItem.id = main.id
                    and related.refund.refundStatus in :statuses
              )
          )
          and not exists (
              select entry.id
              from CourseLedger entry
              where entry.courseRefundItem.id = main.id
                and entry.entryType = :cancellationType
          )
        """)
    Long sumUnappliedCancelledVisitsOfCourse(
            @Param("courseId") Long courseId,
            @Param("statuses") List<RefundStatus> statuses,
            @Param("cancellationType") CourseLedgerEntryType cancellationType);

    @Query("select coalesce(sum(i.cancelledVisits), 0) from CourseRefundItem i "
            + "where i.course.id = :courseId and i.refund.refundStatus in :statuses")
    Long sumCancelledVisitsOfCourse(@Param("courseId") Long courseId, @Param("statuses") List<RefundStatus> statuses);

    /**
     * Reserves pending refunds and counts completed refunds against one
     * course's collected amount from a specific original payment.
     */
    default BigDecimal refundedOrPendingAmountOfCoursePayment(
            Long courseId,
            Long paymentId
    ) {
        return sumRefundAmountOfCoursePayment(
                courseId,
                paymentId,
                List.of(RefundStatus.Pending, RefundStatus.Completed)
        );
    }

    /**
     * Sums refund items by course, original payment and selected statuses.
     */
    @Query("""
        select coalesce(sum(i.refundAmount), 0)
        from CourseRefundItem i
        where i.course.id = :courseId
            and i.refund.originalPayment.id = :paymentId
            and i.refund.refundStatus in :statuses
    """)
    BigDecimal sumRefundAmountOfCoursePayment(
            @Param("courseId") Long courseId,
            @Param("paymentId") Long paymentId,
            @Param("statuses") List<RefundStatus> statuses
    );
}
