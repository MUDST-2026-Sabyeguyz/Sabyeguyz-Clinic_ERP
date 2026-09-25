package com.sabyeguyz.clinicv2.sale;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_method")
public class PaymentMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_method_id")
    private Long id;

    /** e.g. Cash, Transfer, QR, Credit Card. */
    @Column(name = "method_name", length = 50, nullable = false)
    private String methodName;

    @Column(name = "payment_method_status", nullable = false)
    private boolean paymentMethodStatus = true;

    @Column(name = "payment_method_create_at", nullable = false, updatable = false)
    private Instant paymentMethodCreateAt;

    @Column(name = "payment_method_create_by", nullable = false, updatable = false)
    private Long paymentMethodCreateBy;

    @Column(name = "payment_method_update_at")
    private Instant paymentMethodUpdateAt;

    @Column(name = "payment_method_update_by")
    private Long paymentMethodUpdateBy;

    protected PaymentMethod() {}

    public Long getId() { return id; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public boolean isPaymentMethodStatus() { return paymentMethodStatus; }
    public void setPaymentMethodStatus(boolean paymentMethodStatus) { this.paymentMethodStatus = paymentMethodStatus; }
    public Instant getPaymentMethodCreateAt() { return paymentMethodCreateAt; }
    public void setPaymentMethodCreateAt(Instant paymentMethodCreateAt) { this.paymentMethodCreateAt = paymentMethodCreateAt; }
    public Long getPaymentMethodCreateBy() { return paymentMethodCreateBy; }
    public void setPaymentMethodCreateBy(Long paymentMethodCreateBy) { this.paymentMethodCreateBy = paymentMethodCreateBy; }
    public Instant getPaymentMethodUpdateAt() { return paymentMethodUpdateAt; }
    public void setPaymentMethodUpdateAt(Instant paymentMethodUpdateAt) { this.paymentMethodUpdateAt = paymentMethodUpdateAt; }
    public Long getPaymentMethodUpdateBy() { return paymentMethodUpdateBy; }
    public void setPaymentMethodUpdateBy(Long paymentMethodUpdateBy) { this.paymentMethodUpdateBy = paymentMethodUpdateBy; }
}
