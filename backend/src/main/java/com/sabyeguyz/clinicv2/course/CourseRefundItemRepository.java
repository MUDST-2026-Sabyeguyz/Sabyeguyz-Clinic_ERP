package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.enums.RefundStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRefundItemRepository extends JpaRepository<CourseRefundItem, Long> {
    List<CourseRefundItem> findByRefundIdOrderByIdAsc(Long refundId);

    /** What Pending and Completed refunds already took from a course (Failed and Voided free the amount again). */
    default BigDecimal refundedAmountOfCourse(Long courseId) {
        return sumRefundAmountOfCourse(courseId, List.of(RefundStatus.Pending, RefundStatus.Completed));
    }

    /** The status list is a parameter: a PostgreSQL enum literal in JPQL is cast to the wrong type name. */
    @Query("select coalesce(sum(i.refundAmount), 0) from CourseRefundItem i "
            + "where i.course.id = :courseId and i.refund.refundStatus in :statuses")
    BigDecimal sumRefundAmountOfCourse(@Param("courseId") Long courseId, @Param("statuses") List<RefundStatus> statuses);

    /** Rights that Pending refunds already ask to cancel on a course. */
    default int pendingCancelledVisitsOfCourse(Long courseId) {
        Long total = sumCancelledVisitsOfCourse(courseId, List.of(RefundStatus.Pending));
        return total == null ? 0 : total.intValue();
    }

    @Query("select coalesce(sum(i.cancelledVisits), 0) from CourseRefundItem i "
            + "where i.course.id = :courseId and i.refund.refundStatus in :statuses")
    Long sumCancelledVisitsOfCourse(@Param("courseId") Long courseId, @Param("statuses") List<RefundStatus> statuses);
}
