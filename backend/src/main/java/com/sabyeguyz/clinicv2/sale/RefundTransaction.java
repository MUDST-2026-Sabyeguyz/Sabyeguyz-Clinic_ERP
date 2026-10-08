package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.enums.RefundStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Money returned against a payment. Every refund hangs off a Refund
 * cancellation; a Completed refund carries refunded_at/by, any other status
 * carries neither (the database enforces this too).
 */
@Entity
@Table(name = "refund_transaction")
public class RefundTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancellation_id")
    private TransactionCancellation cancellation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_payment_id")
    private PaymentTransaction originalPayment;

    @Column(name = "refund_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal refundAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "refund_status", nullable = false, columnDefinition = "refund_status_enum")
    private RefundStatus refundStatus;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refunded_by")
    private Long refundedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    public RefundTransaction() {}

    public Long getId() { return id; }
    public TransactionCancellation getCancellation() { return cancellation; }
    public void setCancellation(TransactionCancellation cancellation) { this.cancellation = cancellation; }
    public PaymentTransaction getOriginalPayment() { return originalPayment; }
    public void setOriginalPayment(PaymentTransaction originalPayment) { this.originalPayment = originalPayment; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public RefundStatus getRefundStatus() { return refundStatus; }
    public void setRefundStatus(RefundStatus refundStatus) { this.refundStatus = refundStatus; }
    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }
    public Long getRefundedBy() { return refundedBy; }
    public void setRefundedBy(Long refundedBy) { this.refundedBy = refundedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
}
