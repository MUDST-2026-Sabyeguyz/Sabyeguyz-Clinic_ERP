package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import com.sabyeguyz.clinicv2.branch.Branch;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.enums.AdjustmentStatus;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.staff.Staff;

/** Signed correction (positive = pay more, negative = propose a deduction) of a month that is already paid, applied to a later month. It never changes the paid ledger, the tier or the course rate. */
@Entity
@Table(name = "staff_pay_adjustment")
public class StaffPayAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "adjustment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "automatic", nullable = false)
    private boolean automatic;

    @Column(name = "approval_reference", length = 255)
    private String approvalReference;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "original_month", length = 7, nullable = false)
    private String originalMonth;

    /** Month the correction is paid in; later than the original month. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "applied_month", length = 7, nullable = false)
    private String appliedMonth;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    /** At least one of the five sources is required. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id")
    private CommissionAllocationLedger allocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "single_visit_fee_id")
    private SingleVisitFeeLedger singleVisitFee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id")
    private PatientVisit visit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_transaction_id")
    private SalesTransaction saleTransaction;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Sum of the Applied applications. */
    @Column(name = "processed_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal processedAmount = BigDecimal.ZERO;

    /** |amount| - processed amount. */
    @Column(name = "remaining_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal remainingAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "adjustment_status", nullable = false, columnDefinition = "adjustment_status_enum")
    private AdjustmentStatus adjustmentStatus = AdjustmentStatus.Pending;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public StaffPayAdjustment() {}

    public Long getId() { return id; }

    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public boolean isAutomatic() { return automatic; }
    public void setAutomatic(boolean automatic) { this.automatic = automatic; }
    public String getApprovalReference() { return approvalReference; }
    public void setApprovalReference(String approvalReference) { this.approvalReference = approvalReference; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getOriginalMonth() { return originalMonth; }
    public void setOriginalMonth(String originalMonth) { this.originalMonth = originalMonth; }

    public String getAppliedMonth() { return appliedMonth; }
    public void setAppliedMonth(String appliedMonth) { this.appliedMonth = appliedMonth; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public CommissionAllocationLedger getAllocation() { return allocation; }
    public void setAllocation(CommissionAllocationLedger allocation) { this.allocation = allocation; }

    public SingleVisitFeeLedger getSingleVisitFee() { return singleVisitFee; }
    public void setSingleVisitFee(SingleVisitFeeLedger singleVisitFee) { this.singleVisitFee = singleVisitFee; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public PatientVisit getVisit() { return visit; }
    public void setVisit(PatientVisit visit) { this.visit = visit; }

    public SalesTransaction getSaleTransaction() { return saleTransaction; }
    public void setSaleTransaction(SalesTransaction saleTransaction) { this.saleTransaction = saleTransaction; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public BigDecimal getProcessedAmount() { return processedAmount; }
    public void setProcessedAmount(BigDecimal processedAmount) { this.processedAmount = processedAmount; }

    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }

    public AdjustmentStatus getAdjustmentStatus() { return adjustmentStatus; }
    public void setAdjustmentStatus(AdjustmentStatus adjustmentStatus) { this.adjustmentStatus = adjustmentStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
