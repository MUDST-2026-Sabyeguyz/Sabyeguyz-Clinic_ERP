package com.sabyeguyz.clinicv2.commission;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.sabyeguyz.clinicv2.enums.ApplicationStatus;

/** Part (or all) of an adjustment placed in one monthly payout. Draft reserves the amount; Applied is final and is confirmed together with the payout being Paid. */
@Entity
@Table(name = "adjustment_application")
public class AdjustmentApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adjustment_id")
    private StaffPayAdjustment adjustment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monthly_payment_id")
    private StaffMonthlyPayment monthlyPayment;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "application_status", nullable = false, columnDefinition = "application_status_enum")
    private ApplicationStatus applicationStatus = ApplicationStatus.Draft;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Required to apply a deduction (the clinic confirms the basis; the system does not judge the law). */
    @Column(name = "approval_reference", length = 255)
    private String approvalReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public AdjustmentApplication() {}

    public Long getId() { return id; }
    public StaffPayAdjustment getAdjustment() { return adjustment; }
    public void setAdjustment(StaffPayAdjustment adjustment) { this.adjustment = adjustment; }
    public StaffMonthlyPayment getMonthlyPayment() { return monthlyPayment; }
    public void setMonthlyPayment(StaffMonthlyPayment monthlyPayment) { this.monthlyPayment = monthlyPayment; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public ApplicationStatus getApplicationStatus() { return applicationStatus; }
    public void setApplicationStatus(ApplicationStatus applicationStatus) { this.applicationStatus = applicationStatus; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovalReference() { return approvalReference; }
    public void setApprovalReference(String approvalReference) { this.approvalReference = approvalReference; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
