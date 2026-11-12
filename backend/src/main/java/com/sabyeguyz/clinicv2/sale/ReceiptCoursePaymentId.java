package com.sabyeguyz.clinicv2.sale;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ReceiptCoursePaymentId implements Serializable {

    @Column(name = "receipt_id")
    private Long receiptId;

    @Column(name = "course_id")
    private Long courseId;

    public ReceiptCoursePaymentId() {}

    public ReceiptCoursePaymentId(
            Long receiptId,
            Long courseId) {
        this.receiptId = receiptId;
        this.courseId = courseId;
    }

    public Long getReceiptId() { return receiptId; }

    public Long getCourseId() { return courseId; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;

        if (!(object instanceof ReceiptCoursePaymentId other)) return false;

        return Objects.equals(receiptId, other.receiptId)
                && Objects.equals(courseId, other.courseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiptId, courseId);
    }
}
