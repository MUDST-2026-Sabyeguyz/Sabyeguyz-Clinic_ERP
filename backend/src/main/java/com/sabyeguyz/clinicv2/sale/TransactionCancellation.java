package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.course.CourseTransfer;
import com.sabyeguyz.clinicv2.course.CourseVisitUsage;
import com.sabyeguyz.clinicv2.role.Permission;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transaction_cancellation")
public class TransactionCancellation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancellation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private SalesTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private PaymentTransaction payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private CourseTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_usage_id")
    private CourseVisitUsage visitUsage;

    @Column(length = 255, nullable = false)
    private String reason;

    @Column(name = "cancelled_by", nullable = false)
    private Long cancelledBy;

    @Column(name = "cancelled_at", nullable = false)
    private Instant cancelledAt;

    /** Adjustment record created to reverse the effect. */
    @Column(name = "reversal_reference_id")
    private Long reversalReferenceId;

    protected TransactionCancellation() {}

    public Long getId() { return id; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
    public SalesTransaction getTransaction() { return transaction; }
    public void setTransaction(SalesTransaction transaction) { this.transaction = transaction; }
    public PaymentTransaction getPayment() { return payment; }
    public void setPayment(PaymentTransaction payment) { this.payment = payment; }
    public CourseTransfer getTransfer() { return transfer; }
    public void setTransfer(CourseTransfer transfer) { this.transfer = transfer; }
    public CourseVisitUsage getVisitUsage() { return visitUsage; }
    public void setVisitUsage(CourseVisitUsage visitUsage) { this.visitUsage = visitUsage; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public Long getReversalReferenceId() { return reversalReferenceId; }
    public void setReversalReferenceId(Long reversalReferenceId) { this.reversalReferenceId = reversalReferenceId; }
}
