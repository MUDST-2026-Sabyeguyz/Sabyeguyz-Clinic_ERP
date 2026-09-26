package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.patient.Patient;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "receipt")
public class Receipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receipt_id")
    private Long id;

    @Column(name = "receipt_no", length = 30, nullable = false, unique = true)
    private String receiptNo;

    /** Case owner patient. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "receipt_create_at", nullable = false, updatable = false)
    private Instant receiptCreateAt;

    @Column(name = "receipt_create_by", nullable = false, updatable = false)
    private Long receiptCreateBy;

    @Column(name = "receipt_update_at")
    private Instant receiptUpdateAt;

    @Column(name = "receipt_update_by")
    private Long receiptUpdateBy;

    public Receipt() {}

    public Long getId() { return id; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }
    public Instant getReceiptCreateAt() { return receiptCreateAt; }
    public void setReceiptCreateAt(Instant receiptCreateAt) { this.receiptCreateAt = receiptCreateAt; }
    public Long getReceiptCreateBy() { return receiptCreateBy; }
    public void setReceiptCreateBy(Long receiptCreateBy) { this.receiptCreateBy = receiptCreateBy; }
    public Instant getReceiptUpdateAt() { return receiptUpdateAt; }
    public void setReceiptUpdateAt(Instant receiptUpdateAt) { this.receiptUpdateAt = receiptUpdateAt; }
    public Long getReceiptUpdateBy() { return receiptUpdateBy; }
    public void setReceiptUpdateBy(Long receiptUpdateBy) { this.receiptUpdateBy = receiptUpdateBy; }
}
