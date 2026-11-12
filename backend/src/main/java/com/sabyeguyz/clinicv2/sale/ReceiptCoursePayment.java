package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.catalog.Course;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "receipt_course_payment")
public class ReceiptCoursePayment {

    @EmbeddedId
    private ReceiptCoursePaymentId id = new ReceiptCoursePaymentId();

    @MapsId("receiptId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @MapsId("courseId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "item_name_snapshot", length = 255, nullable = false)
    private String itemNameSnapshot;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(
            name = "allocated_bill_discount_amount",
            precision = 12,
            scale = 2,
            nullable = false
    )
    private BigDecimal allocatedBillDiscountAmount;

    public ReceiptCoursePayment() {}

    public ReceiptCoursePaymentId getId() {
        return id;
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public void setReceipt(Receipt receipt) {
        this.receipt = receipt;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getItemNameSnapshot() {
        return itemNameSnapshot;
    }

    public void setItemNameSnapshot(String itemNameSnapshot) {
        this.itemNameSnapshot = itemNameSnapshot;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAllocatedBillDiscountAmount() {
        return allocatedBillDiscountAmount;
    }

    public void setAllocatedBillDiscountAmount(
            BigDecimal allocatedBillDiscountAmount
    ) {
        this.allocatedBillDiscountAmount = allocatedBillDiscountAmount;
    }
}