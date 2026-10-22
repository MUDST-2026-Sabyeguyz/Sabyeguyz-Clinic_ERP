package com.sabyeguyz.clinicv2.appointment;

import jakarta.persistence.*;

/** Document links, not completion flags: reversals must be read from the original ledger. */
@Entity
@Table(name = "appointment_process")
public class AppointmentProcess {
    @Id @Column(name = "appointment_id") private Long appointmentId;
    @Column(name = "receipt_id", unique = true) private Long receiptId;
    @Column(name = "usage_id", unique = true) private Long usageId;
    @Column(name = "payment_fingerprint", length = 64) private String paymentFingerprint;
    @Column(name = "requires_review", nullable = false) private boolean requiresReview;
    public AppointmentProcess() {}
    public AppointmentProcess(Long id) { appointmentId = id; }
    public Long getAppointmentId() { return appointmentId; }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long value) { receiptId = value; }
    public Long getUsageId() { return usageId; }
    public void setUsageId(Long value) { usageId = value; }
    public String getPaymentFingerprint() { return paymentFingerprint; }
    public void setPaymentFingerprint(String value) { paymentFingerprint = value; }
    public boolean isRequiresReview() { return requiresReview; }
    public void setRequiresReview(boolean value) { requiresReview = value; }
}
