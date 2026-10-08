package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseService;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a Sales_transaction (Clinic ERP Workflow Brief §5). For
 * transactionType = CoursePurchase, this also creates the Course via
 * {@link CourseService} (which references this sale line) so the two always
 * happen together in one transaction — a CoursePurchase row can never end up
 * without its Course.
 */
@org.springframework.stereotype.Service
public class SalesTransactionService {
    private final SalesTransactionRepository transactions;
    private final CourseService courseService;

    public SalesTransactionService(SalesTransactionRepository transactions, CourseService courseService) {
        this.transactions = transactions;
        this.courseService = courseService;
    }

    /** SingleVisit: a plain priced line, no Course involved. Assessment is no longer sold. */
    @Transactional
    public SalesTransaction recordVisitSale(Patient patient, Branch branch, Service service, TransactionType type,
                                             Staff treatingStaff, Staff salespersonStaff, int quantity,
                                             BigDecimal unitPrice, PatientVisit visit, Discount discount,
                                             Long actorId) {
        require(type == TransactionType.SingleVisit, "recordVisitSale ใช้ได้เฉพาะ SingleVisit (ไม่รองรับ Assessment แล้ว)");
        BigDecimal price = unitPrice != null ? unitPrice : service.getServicePrice();
        SalesTransaction transaction = base(patient, branch, service, type, treatingStaff, salespersonStaff,
                quantity, price, visit, actorId);
        applyLineDiscount(transaction, discount);
        return transactions.save(transaction);
    }

    /** CoursePurchase: records the sale line first, then the Course that references it. */
    @Transactional
    public SalesTransaction recordCoursePurchase(Patient patient, Branch branch, Receipt receipt, Service courseTemplate,
                                                  Staff sellerStaff, Staff caseOwnerStaff, BigDecimal priceOverride,
                                                  Integer totalVisitsOverride, Integer bonusVisitsOverride,
                                                  Discount discount, LocalDate saleDate, Long actorId) {
        BigDecimal price = priceOverride != null ? priceOverride : courseTemplate.getServicePrice();
        SalesTransaction line = base(patient, branch, courseTemplate, TransactionType.CoursePurchase,
                null, sellerStaff, 1, price, null, actorId);
        applyLineDiscount(line, discount);
        SalesTransaction transaction = transactions.save(line);

        Course course = courseService.sell(receipt, transaction, courseTemplate, sellerStaff, caseOwnerStaff,
                totalVisitsOverride, bonusVisitsOverride, saleDate, actorId);
        // transaction is a managed entity here, so dirty checking persists this back-reference at commit.
        transaction.setCourse(course);
        return transaction;
    }

    /** amount becomes the commissionable price after the line discount; unit_price keeps the list price. */
    private static void applyLineDiscount(SalesTransaction transaction, Discount discount) {
        if (discount != null) {
            transaction.setAmount(discount.apply(transaction.getAmount()));
        }
    }

    private SalesTransaction base(Patient patient, Branch branch, Service service, TransactionType type,
                                   Staff treatingStaff, Staff salespersonStaff, int quantity, BigDecimal unitPrice,
                                   PatientVisit visit, Long actorId) {
        require(patient != null, "ต้องระบุผู้ป่วย");
        require(branch != null, "ต้องระบุสาขา");
        require(service != null, "ต้องระบุบริการ");
        require(quantity >= 1, "quantity ต้องอย่างน้อย 1");
        require(unitPrice != null && unitPrice.signum() >= 0, "unitPrice ต้องไม่ติดลบ");

        SalesTransaction transaction = new SalesTransaction();
        transaction.setVisit(visit);
        transaction.setPatient(patient);
        transaction.setBranch(branch);
        transaction.setService(service);
        transaction.setTransactionType(type);
        transaction.setTreatingStaff(treatingStaff);
        transaction.setSalespersonStaff(salespersonStaff);
        transaction.setQuantity(quantity);
        transaction.setUnitPrice(unitPrice);
        transaction.setAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        transaction.setTransactionDate(Instant.now());
        transaction.setTransactionStatus(true);
        transaction.setTransactionCreateAt(Instant.now());
        transaction.setTransactionCreateBy(actorId);
        return transaction;
    }

    /** Reverses a transaction without deleting it (Clinic ERP Workflow Brief §6 "Cancellation/Void"). */
    @Transactional
    public SalesTransaction voidTransaction(Long transactionId, Long actorId) {
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
