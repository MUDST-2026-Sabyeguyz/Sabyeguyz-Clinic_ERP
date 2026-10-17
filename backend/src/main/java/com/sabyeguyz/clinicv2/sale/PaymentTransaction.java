package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.patient.Patient;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transaction")
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @Column(name = "payment_date", nullable = false)
    private Instant paymentDate;

    /** false = Voided, true = Completed. */
    @Column(name = "payment_status", nullable = false)
    private boolean paymentStatus = true;

    @Column(name = "payment_create_at", nullable = false, updatable = false)
    private Instant paymentCreateAt;

    @Column(name = "payment_create_by", nullable = false, updatable = false)
    private Long paymentCreateBy;

    @Column(name = "payment_update_at")
    private Instant paymentUpdateAt;

    @Column(name = "payment_update_by")
    private Long paymentUpdateBy;

    public PaymentTransaction() {}

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public Receipt getReceipt() { return receipt; }
    public void setReceipt(Receipt receipt) { this.receipt = receipt; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public Instant getPaymentDate() { return paymentDate; }
    public void setPaymentDate(Instant paymentDate) { this.paymentDate = paymentDate; }
    public boolean isPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(boolean paymentStatus) { this.paymentStatus = paymentStatus; }
    public Instant getPaymentCreateAt() { return paymentCreateAt; }
    public void setPaymentCreateAt(Instant paymentCreateAt) { this.paymentCreateAt = paymentCreateAt; }
    public Long getPaymentCreateBy() { return paymentCreateBy; }
    public void setPaymentCreateBy(Long paymentCreateBy) { this.paymentCreateBy = paymentCreateBy; }
    public Instant getPaymentUpdateAt() { return paymentUpdateAt; }
    public void setPaymentUpdateAt(Instant paymentUpdateAt) { this.paymentUpdateAt = paymentUpdateAt; }
    public Long getPaymentUpdateBy() { return paymentUpdateBy; }
    public void setPaymentUpdateBy(Long paymentUpdateBy) { this.paymentUpdateBy = paymentUpdateBy; }
}
