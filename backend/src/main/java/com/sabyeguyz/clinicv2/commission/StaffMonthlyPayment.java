package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.PaymentStatus;
import com.sabyeguyz.clinicv2.staff.Staff;

/** Monthly payout summary of one staff member: owner commission, single-visit fees, substitute fees and applied adjustments. payment_month is the month of treatment, not the month the course was sold. */
@Entity
@Table(name = "staff_monthly_payment")
public class StaffMonthlyPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "salary_payment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    /** Historical branch of this monthly record. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payment_month", length = 7, nullable = false)
    private String paymentMonth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "payment_status", nullable = false, columnDefinition = "payment_status_enum")
    private PaymentStatus paymentStatus = PaymentStatus.Open;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** The date the Manager says the money was paid, and the reference (transfer / slip number) of that payment. */
    @Column(name = "paid_date")
    private java.time.LocalDate paidDate;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "status_changed_by", nullable = false)
    private Long statusChangedBy;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    /** The monthly closing whose rate the commission used, when any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closing_id")
    private MonthlyCommissionClosing closing;

    @Column(name = "course_commission_amount", precision = 12, scale = 2)
    private BigDecimal courseCommissionAmount;

    @Column(name = "single_visit_fee_amount", precision = 12, scale = 2)
    private BigDecimal singleVisitFeeAmount;

    @Column(name = "substitute_fee_amount", precision = 12, scale = 2)
    private BigDecimal substituteFeeAmount;

    @Column(name = "adjustment_amount", precision = 12, scale = 2)
    private BigDecimal adjustmentAmount;

    /** Sum of the four amounts above; never negative. */
    @Column(name = "net_payment_amount", precision = 12, scale = 2)
    private BigDecimal netPaymentAmount;

    public StaffMonthlyPayment() {}

    public Long getId() { return id; }

    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }

    public String getPaymentMonth() { return paymentMonth; }
    public void setPaymentMonth(String paymentMonth) { this.paymentMonth = paymentMonth; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public java.time.LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(java.time.LocalDate paidDate) { this.paidDate = paidDate; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Long getStatusChangedBy() { return statusChangedBy; }
    public void setStatusChangedBy(Long statusChangedBy) { this.statusChangedBy = statusChangedBy; }

    public Instant getStatusChangedAt() { return statusChangedAt; }
    public void setStatusChangedAt(Instant statusChangedAt) { this.statusChangedAt = statusChangedAt; }

    public MonthlyCommissionClosing getClosing() { return closing; }
    public void setClosing(MonthlyCommissionClosing closing) { this.closing = closing; }

    public BigDecimal getCourseCommissionAmount() { return courseCommissionAmount; }
    public void setCourseCommissionAmount(BigDecimal courseCommissionAmount) { this.courseCommissionAmount = courseCommissionAmount; }

    public BigDecimal getSingleVisitFeeAmount() { return singleVisitFeeAmount; }
    public void setSingleVisitFeeAmount(BigDecimal singleVisitFeeAmount) { this.singleVisitFeeAmount = singleVisitFeeAmount; }

    public BigDecimal getSubstituteFeeAmount() { return substituteFeeAmount; }
    public void setSubstituteFeeAmount(BigDecimal substituteFeeAmount) { this.substituteFeeAmount = substituteFeeAmount; }

    public BigDecimal getAdjustmentAmount() { return adjustmentAmount; }
    public void setAdjustmentAmount(BigDecimal adjustmentAmount) { this.adjustmentAmount = adjustmentAmount; }

    public BigDecimal getNetPaymentAmount() { return netPaymentAmount; }
    public void setNetPaymentAmount(BigDecimal netPaymentAmount) { this.netPaymentAmount = netPaymentAmount; }
}
