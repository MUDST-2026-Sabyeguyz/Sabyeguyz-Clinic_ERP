package com.sabyeguyz.clinicv2.sale;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptCoursePaymentRepository
        extends JpaRepository<ReceiptCoursePayment, ReceiptCoursePaymentId> {

    List<ReceiptCoursePayment> findByReceipt_IdOrderByCourse_IdAsc(
            Long receiptId
    );

    List<ReceiptCoursePayment> findByCourse_IdOrderByReceipt_IdAsc(
            Long courseId
    );

    /**
     * Checks whether the receipt contains existing-course collection items.
     */
    boolean existsByReceipt_Id(Long receiptId);
}