package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sales_transaction")
public class SalesTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id")
    private PatientVisit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private Service service;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "transaction_type", nullable = false, columnDefinition = "transaction_type_enum")
    private TransactionType transactionType;

    /** Treating PT (who performs the service). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treating_staff_id")
    private Staff treatingStaff;

    /** Seller / Case Owner PT. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesperson_staff_id")
    private Staff salespersonStaff;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    /** false = Cancelled, true = Completed. */
    @Column(name = "transaction_status", nullable = false)
    private boolean transactionStatus = true;

    @Column(name = "transaction_create_at", nullable = false, updatable = false)
    private Instant transactionCreateAt;

    @Column(name = "transaction_create_by", nullable = false, updatable = false)
    private Long transactionCreateBy;

    @Column(name = "transaction_update_at")
    private Instant transactionUpdateAt;

    @Column(name = "transaction_update_by")
    private Long transactionUpdateBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "line_discount_type", columnDefinition = "line_discount_type_enum")
    private DiscountType lineDiscountType;

    @Column(name = "line_discount_value", precision = 12, scale = 4)
    private BigDecimal lineDiscountValue;

    @Column(name = "line_discount_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal lineDiscountAmount = BigDecimal.ZERO;

    @Column(name = "allocated_bill_discount_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal allocatedBillDiscountAmount = BigDecimal.ZERO;

    /** Price after the line discount and before any bill discount; this is what Tier calculation uses. */
    @Column(name = "commissionable_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal commissionableAmount = BigDecimal.ZERO;

    @Column(name = "item_name_snapshot", length = 200, nullable = false)
    private String itemNameSnapshot;

    @Column(name = "unit_name_snapshot", length = 30, nullable = false)
    private String unitNameSnapshot;

    public SalesTransaction() {}

    public Long getId() { return id; }
    public PatientVisit getVisit() { return visit; }
    public void setVisit(PatientVisit visit) { this.visit = visit; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    public Staff getTreatingStaff() { return treatingStaff; }
    public void setTreatingStaff(Staff treatingStaff) { this.treatingStaff = treatingStaff; }
    public Staff getSalespersonStaff() { return salespersonStaff; }
    public void setSalespersonStaff(Staff salespersonStaff) { this.salespersonStaff = salespersonStaff; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Instant getTransactionDate() { return transactionDate; }
    public void setTransactionDate(Instant transactionDate) { this.transactionDate = transactionDate; }
    public boolean isTransactionStatus() { return transactionStatus; }
    public void setTransactionStatus(boolean transactionStatus) { this.transactionStatus = transactionStatus; }
    public Instant getTransactionCreateAt() { return transactionCreateAt; }
    public void setTransactionCreateAt(Instant transactionCreateAt) { this.transactionCreateAt = transactionCreateAt; }
    public Long getTransactionCreateBy() { return transactionCreateBy; }
    public void setTransactionCreateBy(Long transactionCreateBy) { this.transactionCreateBy = transactionCreateBy; }
    public Instant getTransactionUpdateAt() { return transactionUpdateAt; }
    public void setTransactionUpdateAt(Instant transactionUpdateAt) { this.transactionUpdateAt = transactionUpdateAt; }
    public Long getTransactionUpdateBy() { return transactionUpdateBy; }
    public void setTransactionUpdateBy(Long transactionUpdateBy) { this.transactionUpdateBy = transactionUpdateBy; }
    public Receipt getReceipt() { return receipt; }
    public void setReceipt(Receipt receipt) { this.receipt = receipt; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }
    public DiscountType getLineDiscountType() { return lineDiscountType; }
    public void setLineDiscountType(DiscountType lineDiscountType) { this.lineDiscountType = lineDiscountType; }
    public BigDecimal getLineDiscountValue() { return lineDiscountValue; }
    public void setLineDiscountValue(BigDecimal lineDiscountValue) { this.lineDiscountValue = lineDiscountValue; }
    public BigDecimal getLineDiscountAmount() { return lineDiscountAmount; }
    public void setLineDiscountAmount(BigDecimal lineDiscountAmount) { this.lineDiscountAmount = lineDiscountAmount; }
    public BigDecimal getAllocatedBillDiscountAmount() { return allocatedBillDiscountAmount; }
    public void setAllocatedBillDiscountAmount(BigDecimal allocatedBillDiscountAmount) { this.allocatedBillDiscountAmount = allocatedBillDiscountAmount; }
    public BigDecimal getCommissionableAmount() { return commissionableAmount; }
    public void setCommissionableAmount(BigDecimal commissionableAmount) { this.commissionableAmount = commissionableAmount; }
    public String getItemNameSnapshot() { return itemNameSnapshot; }
    public void setItemNameSnapshot(String itemNameSnapshot) { this.itemNameSnapshot = itemNameSnapshot; }
    public String getUnitNameSnapshot() { return unitNameSnapshot; }
    public void setUnitNameSnapshot(String unitNameSnapshot) { this.unitNameSnapshot = unitNameSnapshot; }
}
