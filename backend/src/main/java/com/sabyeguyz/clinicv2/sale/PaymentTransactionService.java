package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.ReceiptStatus;
import com.sabyeguyz.clinicv2.patient.Patient;

/** Payment collection against a Receipt (Clinic ERP Workflow Brief Section 6 "Payment"). */
@Service
public class PaymentTransactionService {
    private final PaymentTransactionRepository payments;
    private final ReceiptRepository receipts;

    public PaymentTransactionService(
            PaymentTransactionRepository payments,
            ReceiptRepository receipts) {
        this.payments = payments;
        this.receipts = receipts;
    }

    @Transactional
    public PaymentTransaction record(
            Patient patient,
            Branch branch,
            Receipt receipt,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            Long actorId) {
        require(patient != null, "ต้องระบุผู้ป่วย");
        require(branch != null, "ต้องระบุสาขา");
        require(receipt != null, "ต้องระบุใบเสร็จ");
        receipt = receipts.findByIdForUpdate(receipt.getId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบใบเสร็จ"));
        require(patient.getId() != null && patient.getId().equals(receipt.getPatient().getId()),
                "ผู้ป่วยของรายการชำระเงินต้องตรงกับใบเสร็จ");
        require(branch.getId() != null && branch.getId().equals(receipt.getBranch().getId()),
                "สาขาของรายการชำระเงินต้องตรงกับใบเสร็จ");
        require(
                receipt.getReceiptStatus() == ReceiptStatus.Issued,
                "ใบเสร็จนี้ถูกยกเลิกแล้ว ไม่สามารถรับชำระได้");
        require(paymentMethod != null, "ต้องระบุช่องทางชำระเงิน");
        require(paymentMethod.isPaymentMethodStatus(), "ช่องทางการชำระเงินนี้ถูกปิดใช้งาน");
        require(amount != null && amount.signum() >= 0, "จำนวนเงินต้องไม่ติดลบ");

        PaymentMethod.canonicalName(paymentMethod.getMethodName());

        require(!payments.existsByReceiptIdAndPaymentStatusTrue(receipt.getId()),
                "ใบเสร็จนี้มีรายการชำระเงินที่สำเร็จแล้ว");

        require(receipt.getNetAmount() != null && amount.compareTo(receipt.getNetAmount()) == 0,
                "จำนวนเงินที่ชำระต้องเท่ากับยอดสุทธิของใบเสร็จ");

        PaymentTransaction payment = new PaymentTransaction();
        payment.setPatient(patient);
        payment.setBranch(branch);
        payment.setReceipt(receipt);
        payment.setAmount(amount);
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentDate(Instant.now());
        payment.setPaymentStatus(true);
        payment.setPaymentCreateAt(Instant.now());
        payment.setPaymentCreateBy(actorId);
        PaymentTransaction saved = payments.saveAndFlush(payment);

        return saved;
    }

    /** Voids the payment (does not delete it) - see Clinic ERP Workflow Brief Section 6 "Cancellation/Void". */
    @Transactional
    public PaymentTransaction voidPayment(
            Long paymentId,
            Long actorId) {
        PaymentTransaction payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการชำระเงิน"));
        receipts.findByIdForUpdate(payment.getReceipt().getId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบใบเสร็จ"));

        if (!payment.isPaymentStatus()) {
            throw new IllegalArgumentException("รายการนี้ถูกยกเลิกไปแล้ว");
        }

        payment.setPaymentStatus(false);
        payment.setPaymentUpdateAt(Instant.now());
        payment.setPaymentUpdateBy(actorId);
        PaymentTransaction saved = payments.saveAndFlush(payment);

        return saved;
    }

    private static void require(
            boolean condition,
            String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
