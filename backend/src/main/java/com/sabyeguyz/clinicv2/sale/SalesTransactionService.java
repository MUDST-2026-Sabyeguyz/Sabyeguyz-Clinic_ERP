package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseService;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;

/**
 * Records a Sales_transaction line on a Receipt (Clinic ERP Workflow Brief
 * Section 5). For transactionType = CoursePurchase, this also creates the
 * Course via {@link CourseService} (which references this sale line) so the
 * two always happen together in one transaction - a CoursePurchase row can
 * never end up without its Course.
 *
 * <p>amount is the line total after its line discount, which is also the
 * commissionable_amount; the bill discount is allocated later by
 * {@link ReceiptService#finalizeTotals} and never reduces either.</p>
 */
@org.springframework.stereotype.Service
public class SalesTransactionService {
    private static final String UNIT_VISIT = "ครั้ง";
    private static final String UNIT_COURSE = "คอร์ส";

    private final SalesTransactionRepository transactions;
    private final CourseService courseService;

    public SalesTransactionService(
            SalesTransactionRepository transactions,
            CourseService courseService) {
        this.transactions = transactions;
        this.courseService = courseService;
    }

    public record CoursePurchase(
            SalesTransaction transaction,
            Course course) {}

    /** SingleVisit: a plain priced line, no Course involved. Assessment is no longer sold. */
    @Transactional
    public SalesTransaction recordVisitSale(
            Receipt receipt,
            Patient patient,
            Branch branch,
            Service service,
            TransactionType type,
            Staff treatingStaff,
            Staff salespersonStaff,
            int quantity,
            BigDecimal unitPrice,
            PatientVisit visit,
            Discount discount,
            Long actorId) {
        require(
                type == TransactionType.SingleVisit,
                "recordVisitSale ใช้ได้เฉพาะ SingleVisit (ไม่รองรับ Assessment แล้ว)");
        BigDecimal price = unitPrice != null ? unitPrice : service.getServicePrice();
        SalesTransaction transaction = base(
                receipt,
                patient,
                branch,
                service,
                type,
                treatingStaff,
                salespersonStaff,
                quantity,
                price,
                visit,
                discount,
                UNIT_VISIT,
                actorId);

        return transactions.save(transaction);
    }

    /** CoursePurchase: records the sale line first, then the Course that references it. */
    @Transactional
    public CoursePurchase recordCoursePurchase(
            Receipt receipt,
            Patient patient,
            Branch branch,
            Service courseTemplate,
            Staff sellerStaff,
            BigDecimal priceOverride,
            Integer totalVisitsOverride,
            Integer bonusVisitsOverride,
            Discount discount,
            LocalDate saleDate,
            Long actorId) {
        BigDecimal price = priceOverride != null ? priceOverride : courseTemplate.getServicePrice();
        SalesTransaction transaction = transactions.save(base(
                receipt,
                patient,
                branch,
                courseTemplate,
                TransactionType.CoursePurchase,
                null,
                sellerStaff,
                1,
                price,
                null,
                discount,
                UNIT_COURSE,
                actorId));

        Course course = courseService.sell(
                receipt,
                transaction,
                courseTemplate,
                sellerStaff,
                totalVisitsOverride,
                bonusVisitsOverride,
                saleDate,
                actorId);

        return new CoursePurchase(transaction, course);
    }

    private SalesTransaction base(
            Receipt receipt,
            Patient patient,
            Branch branch,
            Service service,
            TransactionType type,
            Staff treatingStaff,
            Staff salespersonStaff,
            int quantity,
            BigDecimal unitPrice,
            PatientVisit visit,
            Discount discount,
            String unitName,
            Long actorId) {
        require(receipt != null, "ต้องระบุใบเสร็จ");
        require(patient != null, "ต้องระบุผู้ป่วย");
        require(branch != null, "ต้องระบุสาขา");
        require(service != null, "ต้องระบุบริการ");
        require(quantity >= 1, "quantity ต้องอย่างน้อย 1");
        require(unitPrice != null && unitPrice.signum() >= 0, "unitPrice ต้องไม่ติดลบ");

        Discount lineDiscount = discount == null ? Discount.none() : discount;
        BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal lineDiscountAmount = lineDiscount.amountOff(gross);
        BigDecimal amount = gross.subtract(lineDiscountAmount);

        SalesTransaction transaction = new SalesTransaction();
        transaction.setReceipt(receipt);
        transaction.setLineNo((int) transactions.countByReceiptId(receipt.getId()) + 1);
        transaction.setVisit(visit);
        transaction.setPatient(patient);
        transaction.setBranch(branch);
        transaction.setService(service);
        transaction.setTransactionType(type);
        transaction.setTreatingStaff(treatingStaff);
        transaction.setSalespersonStaff(salespersonStaff);
        transaction.setQuantity(quantity);
        transaction.setUnitPrice(unitPrice);
        transaction.setLineDiscountType(lineDiscount.type());
        transaction.setLineDiscountValue(lineDiscount.value());
        transaction.setLineDiscountAmount(lineDiscountAmount);
        transaction.setAmount(amount);
        transaction.setCommissionableAmount(amount);
        transaction.setAllocatedBillDiscountAmount(BigDecimal.ZERO.setScale(2));
        transaction.setItemNameSnapshot(service.getServiceName());
        transaction.setUnitNameSnapshot(unitName);
        transaction.setTransactionDate(Instant.now());
        transaction.setTransactionStatus(true);
        transaction.setTransactionCreateAt(Instant.now());
        transaction.setTransactionCreateBy(actorId);

        return transaction;
    }

    /** Reverses a transaction without deleting it (Clinic ERP Workflow Brief Section 6 "Cancellation/Void"). */
    @Transactional
    public SalesTransaction voidTransaction(
            Long transactionId,
            Long actorId) {
        SalesTransaction transaction = transactions.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการขาย"));

        if (!transaction.isTransactionStatus()) {
            throw new IllegalArgumentException("รายการนี้ถูกยกเลิกไปแล้ว");
        }

        transaction.setTransactionStatus(false);
        transaction.setTransactionUpdateAt(Instant.now());
        transaction.setTransactionUpdateBy(actorId);

        return transactions.save(transaction);
    }

    private static void require(
            boolean condition,
            String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
