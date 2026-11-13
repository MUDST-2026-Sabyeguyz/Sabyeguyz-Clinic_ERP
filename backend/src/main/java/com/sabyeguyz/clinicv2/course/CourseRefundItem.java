package com.sabyeguyz.clinicv2.course;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.sale.RefundTransaction;

/** One course covered by a course refund: the amount refunded for it and the visits cancelled. It never recalculates the commission or fees already earned. */
@Entity
@Table(name = "course_refund_item")
public class CourseRefundItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_refund_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id")
    private RefundTransaction refund;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "refund_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal refundAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rights_cancellation_item_id")
    private CourseRefundItem rightsCancellationItem;

    @Column(name = "cancelled_visits")
    private Integer cancelledVisits;

    public CourseRefundItem() {}

    public Long getId() { return id; }
    public RefundTransaction getRefund() { return refund; }
    public void setRefund(RefundTransaction refund) { this.refund = refund; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public Integer getCancelledVisits() { return cancelledVisits; }
    public void setCancelledVisits(Integer cancelledVisits) { this.cancelledVisits = cancelledVisits; }

    public CourseRefundItem getRightsCancellationItem() {
        return rightsCancellationItem;
    }

    public void setRightsCancellationItem(CourseRefundItem item) {
        this.rightsCancellationItem = item;
    }
}
