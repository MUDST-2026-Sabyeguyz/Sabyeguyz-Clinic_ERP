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

    @Column(name = "cancelled_visits", nullable = false)
    private int cancelledVisits;

    public CourseRefundItem() {}

    public Long getId() { return id; }
    public RefundTransaction getRefund() { return refund; }
    public void setRefund(RefundTransaction refund) { this.refund = refund; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public int getCancelledVisits() { return cancelledVisits; }
    public void setCancelledVisits(int cancelledVisits) { this.cancelledVisits = cancelledVisits; }
}
