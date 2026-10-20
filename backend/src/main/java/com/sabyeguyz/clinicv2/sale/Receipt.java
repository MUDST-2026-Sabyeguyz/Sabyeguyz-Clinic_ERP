package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.ReceiptStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A bill. Line items are Sales_transaction rows. subtotal_before_discount is
 * the sum of the lines after their own line discounts; the bill discount comes
 * off that subtotal and is allocated back onto the lines, but never reduces a
 * line's commissionable_amount.
 */
@Entity
@Table(name = "receipt")
public class Receipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receipt_id")
    private Long id;

    @Column(name = "receipt_no", length = 30, nullable = false, unique = true)
    private String receiptNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "sale_date", nullable = false)
    private LocalDateTime saleDate;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "receipt_create_at", nullable = false, updatable = false)
    private Instant receiptCreateAt;

    @Column(name = "receipt_create_by", nullable = false, updatable = false)
    private Long receiptCreateBy;

    @Column(name = "receipt_update_at")
    private Instant receiptUpdateAt;

    @Column(name = "receipt_update_by")
    private Long receiptUpdateBy;

    @Column(name = "subtotal_before_discount", precision = 12, scale = 2, nullable = false)
    private BigDecimal subtotalBeforeDiscount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "bill_discount_type", columnDefinition = "bill_discount_type_enum")
    private DiscountType billDiscountType;

    @Column(name = "bill_discount_value", precision = 12, scale = 4)
    private BigDecimal billDiscountValue;

    @Column(name = "bill_discount_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal billDiscountAmount = BigDecimal.ZERO;

    /** R32 confirms zero tax: taxable amount is the net after bill discount, with tax amount zero. */
    @Column(name = "taxable_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "receipt_status", nullable = false, columnDefinition = "receipt_status_enum")
    private ReceiptStatus receiptStatus = ReceiptStatus.Issued;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "payment_label", nullable = false, columnDefinition = "payment_label_enum")
    private com.sabyeguyz.clinicv2.enums.PaymentLabel paymentLabel = com.sabyeguyz.clinicv2.enums.PaymentLabel.Deposit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_label_id")
    private PaymentLabelOption paymentLabelOption;

    public com.sabyeguyz.clinicv2.enums.PaymentLabel getPaymentLabel() { return paymentLabel; }
    public void setPaymentLabel(com.sabyeguyz.clinicv2.enums.PaymentLabel value) { paymentLabel = value; }

    public PaymentLabelOption getPaymentLabelOption() {
        return paymentLabelOption;
    }
    public void setPaymentLabelOption(PaymentLabelOption paymentLabelOption) {
        this.paymentLabelOption = paymentLabelOption;
    }

    public Receipt() {}

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Long getId() { return id; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public LocalDateTime getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDateTime saleDate) { this.saleDate = saleDate; }
    public Instant getReceiptCreateAt() { return receiptCreateAt; }
    public void setReceiptCreateAt(Instant receiptCreateAt) { this.receiptCreateAt = receiptCreateAt; }
    public Long getReceiptCreateBy() { return receiptCreateBy; }
    public void setReceiptCreateBy(Long receiptCreateBy) { this.receiptCreateBy = receiptCreateBy; }
    public Instant getReceiptUpdateAt() { return receiptUpdateAt; }
    public void setReceiptUpdateAt(Instant receiptUpdateAt) { this.receiptUpdateAt = receiptUpdateAt; }
    public Long getReceiptUpdateBy() { return receiptUpdateBy; }
    public void setReceiptUpdateBy(Long receiptUpdateBy) { this.receiptUpdateBy = receiptUpdateBy; }
    public BigDecimal getSubtotalBeforeDiscount() { return subtotalBeforeDiscount; }
    public void setSubtotalBeforeDiscount(BigDecimal subtotalBeforeDiscount) { this.subtotalBeforeDiscount = subtotalBeforeDiscount; }
    public DiscountType getBillDiscountType() { return billDiscountType; }
    public void setBillDiscountType(DiscountType billDiscountType) { this.billDiscountType = billDiscountType; }
    public BigDecimal getBillDiscountValue() { return billDiscountValue; }
    public void setBillDiscountValue(BigDecimal billDiscountValue) { this.billDiscountValue = billDiscountValue; }
    public BigDecimal getBillDiscountAmount() { return billDiscountAmount; }
    public void setBillDiscountAmount(BigDecimal billDiscountAmount) { this.billDiscountAmount = billDiscountAmount; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public ReceiptStatus getReceiptStatus() { return receiptStatus; }
    public void setReceiptStatus(ReceiptStatus receiptStatus) { this.receiptStatus = receiptStatus; }
}
