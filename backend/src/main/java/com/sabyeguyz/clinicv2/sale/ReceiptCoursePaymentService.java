package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.ReceiptStatus;

@Service
public class ReceiptCoursePaymentService {

    private final ReceiptCoursePaymentRepository repository;
    private final CourseRepository courses;
    private final BranchAccessPolicy branchAccess;

    public ReceiptCoursePaymentService(
            ReceiptCoursePaymentRepository repository,
            CourseRepository courses,
            BranchAccessPolicy branchAccess) {
        this.repository = repository;
        this.courses = courses;
        this.branchAccess = branchAccess;
    }

    public record ItemRequest(
            Long courseId,
            BigDecimal amount) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ReceiptCoursePayment> prepareAll(
            Receipt receipt,
            List<ItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        Set<Long> courseIds = new HashSet<>();

        // Validate the entire request before preparing any items.
        for (ItemRequest request : requests) {
            if (request == null || request.courseId() == null) {
                throw new IllegalArgumentException("ต้องระบุคอร์สทุกรายการ");
            }

            if (!courseIds.add(request.courseId())) {
                throw new IllegalArgumentException("ห้ามระบุคอร์สเดียวกันซ้ำในใบเสร็จเดียว");
            }
        }

        List<ReceiptCoursePayment> items = new ArrayList<>();

        for (ItemRequest request : requests) {
            items.add(prepare(
                    receipt, request.courseId(), request.amount()
            ));
        }

        return items;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReceiptCoursePayment prepare(
            Receipt receipt,
            Long courseId,
            BigDecimal amount) {
        if (receipt == null || receipt.getId() == null) {
            throw new IllegalArgumentException("ต้องระบุใบเสร็จที่บันทึกแล้ว");
        }

        if (receipt.getPatient() == null || receipt.getBranch() == null) {
            throw new IllegalArgumentException("ใบเสร็จต้องมีผู้ป่วยและสาขา");
        }

        branchAccess.requireAccess(receipt.getBranch());

        if (receipt.getReceiptStatus() != ReceiptStatus.Issued) {
            throw new IllegalArgumentException(
                    "รับเงินได้เฉพาะใบเสร็จที่มีสถานะ Issued"
            );
        }

        if (courseId == null) {
            throw new IllegalArgumentException("ต้องระบุคอร์สเดิม");
        }

        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("ยอดก่อนลดต้องไม่ติดลบ");
        }

        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "ยอดก่อนลดต้องมีทศนิยมไม่เกิน 2 ตำแหน่ง"
            );
        }

        if (amount.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException(
                    "ยอดก่อนลดเกินขนาด decimal(12,2)"
            );
        }

        Course course = courses.findById(courseId)
                .orElseThrow(() ->
                        new IllegalArgumentException("ไม่พบคอร์ส"));

        if (!course.getPurchaserPatient().getId()
                .equals(receipt.getPatient().getId())) {
            throw new IllegalArgumentException(
                    "ผู้ซื้อเดิมของคอร์สต้องตรงกับผู้ป่วยบนใบเสร็จ"
            );
        }

        if (course.getReceipt().getId().equals(receipt.getId())) {
            throw new IllegalArgumentException(
                    "ห้ามบันทึกรับเงินคอร์สเดิมซ้ำในใบซื้อแรก"
            );
        }

        ReceiptCoursePaymentId id =
                new ReceiptCoursePaymentId(receipt.getId(), courseId);

        if (repository.existsById(id)) {
            throw new IllegalArgumentException(
                    "คอร์สนี้มีรายการรับเงินในใบเสร็จนี้แล้ว"
            );
        }

        ReceiptCoursePayment item = new ReceiptCoursePayment();
        item.setReceipt(receipt);
        item.setCourse(course);
        item.setItemNameSnapshot(course.getCourseName());
        item.setAmount(amount.setScale(2, RoundingMode.UNNECESSARY));
        item.setAllocatedBillDiscountAmount(new BigDecimal("0.00"));

        return item;
    }
}
