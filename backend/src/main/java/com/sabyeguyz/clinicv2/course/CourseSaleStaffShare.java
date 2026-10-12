package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * How a course's sale is credited to staff for monthly Tier calculation. Per
 * course the percents total 100 and the credited amounts total course_price;
 * a plain sale credits the seller 100%, a shared sale (two PTs treating two
 * patients together) splits it.
 */
@Entity
@Table(name = "course_sale_staff_share")
@IdClass(CourseSaleStaffShareId.class)
public class CourseSaleStaffShare {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "share_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal sharePercent;

    @Column(name = "credited_sales_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal creditedSalesAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    public CourseSaleStaffShare() {}

    public CourseSaleStaffShare(Course course, Staff staff, BigDecimal sharePercent, BigDecimal creditedSalesAmount,
                                 Long createdBy) {
        this.course = course;
        this.staff = staff;
        this.sharePercent = sharePercent;
        this.creditedSalesAmount = creditedSalesAmount;
        this.createdAt = Instant.now();
        this.createdBy = createdBy;
    }

    public Course getCourse() { return course; }
    public Staff getStaff() { return staff; }
    public BigDecimal getSharePercent() { return sharePercent; }
    public BigDecimal getCreditedSalesAmount() { return creditedSalesAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
}
