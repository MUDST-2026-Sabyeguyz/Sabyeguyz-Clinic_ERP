package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.ReceiptStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Payment collection against a Receipt (Clinic ERP Workflow Brief Section 6 "Payment"). */
@Service
public class PaymentTransactionService {
    private final PaymentTransactionRepository payments;

    public PaymentTransactionService(PaymentTransactionRepository payments) {
        this.payments = payments;
    }

    @Transactional
    public PaymentTransaction record(Patient patient, Branch branch, Receipt receipt,
                                      BigDecimal amount, PaymentMethod paymentMethod, Long actorId) {
        require(patient != null, "ต้องระบุผู้ป่วย");
        require(branch != null, "ต้องระบุสาขา");
        require(receipt != null, "ต้องระบุใบเสร็จ");
        require(receipt.getReceiptStatus() == ReceiptStatus.Issued, "ใบเสร็จนี้ถูกยกเลิกแล้ว ไม่สามารถรับชำระได้");
        require(paymentMethod != null, "ต้องระบุช่องทางชำระเงิน");
        require(amount != null && amount.signum() > 0, "จำนวนเงินต้องมากกว่า 0");

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
        return payments.save(payment);
    }

    /** Voids the payment (does not delete it) - see Clinic ERP Workflow Brief Section 6 "Cancellation/Void". */
    @Transactional
    public PaymentTransaction voidPayment(Long paymentId, Long actorId) {
        PaymentTransaction payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการชำระเงิน"));
        if (!payment.isPaymentStatus()) {
            throw new IllegalArgumentException("รายการนี้ถูกยกเลิกไปแล้ว");
        }
        payment.setPaymentStatus(false);
        payment.setPaymentUpdateAt(Instant.now());
        payment.setPaymentUpdateBy(actorId);
        return payments.save(payment);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
