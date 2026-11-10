package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CoursePaymentSourceService {

    private final CourseRepository courses;
    private final ReceiptCoursePaymentRepository coursePayments;

    public CoursePaymentSourceService(
            CourseRepository courses,
            ReceiptCoursePaymentRepository coursePayments
    ) {
        this.courses = courses;
        this.coursePayments = coursePayments;
    }

    /**
     * Returns this course's net item amount on the specified receipt.
     * The caller must separately validate the original payment and access.
     */
    public BigDecimal netAmountFor(Long receiptId, Long courseId) {
        if (receiptId == null || courseId == null) {
            throw new IllegalArgumentException(
                    "ต้องระบุรหัสใบเสร็จและคอร์ส"
            );
        }

        Course course = courses.findById(courseId)
                .orElseThrow(() ->
                        new IllegalArgumentException("ไม่พบคอร์ส"));

        var collectionItem = coursePayments.findById(
                new ReceiptCoursePaymentId(receiptId, courseId)
        );

        BigDecimal amount;
        BigDecimal allocatedDiscount;

        if (course.getReceipt().getId().equals(receiptId)) {
            if (collectionItem.isPresent()) {
                throw new IllegalStateException(
                        "พบรายการรับเงินคอร์สเดิมซ้ำในใบซื้อแรก"
                );
            }

            SalesTransaction originalSale = course.getSaleTransaction();

            if (!originalSale.getReceipt().getId().equals(receiptId)) {
                throw new IllegalStateException(
                        "ใบเสร็จของรายการขายต้นทางไม่ตรงกับคอร์ส"
                );
            }

            amount = originalSale.getAmount();
            allocatedDiscount =
                    originalSale.getAllocatedBillDiscountAmount();
        } else {
            ReceiptCoursePayment item = collectionItem
                    .orElseThrow(() -> new IllegalArgumentException(
                            "คอร์สนี้ไม่สัมพันธ์กับใบเสร็จที่ระบุ"
                    ));

            amount = item.getAmount();
            allocatedDiscount =
                    item.getAllocatedBillDiscountAmount();
        }

        BigDecimal netAmount = amount.subtract(allocatedDiscount);

        if (netAmount.signum() < 0) {
            throw new IllegalStateException(
                    "ยอดรับสุทธิของคอร์สในใบเสร็จต้องไม่ติดลบ"
            );
        }

        return netAmount;
    }
}