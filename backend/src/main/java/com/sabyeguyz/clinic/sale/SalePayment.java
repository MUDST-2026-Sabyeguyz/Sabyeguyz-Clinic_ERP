package com.sabyeguyz.clinic.sale;

import com.sabyeguyz.clinic.payment.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * รายการรับเงินหรือคืนเงินของใบเสร็จ — เขียนเพิ่มอย่างเดียว ห้ามแก้หรือลบ
 * การคืนเงินคือแถวใหม่ชนิด {@link SalePaymentType#REFUND} ยอดติดลบ ที่ชี้กลับไปยังรายการรับเงินเดิม
 */
@Entity
@Table(name = "sale_payments")
public class SalePayment {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sale_payment_id_generator")
    @SequenceGenerator(name = "sale_payment_id_generator", sequenceName = "sale_payment_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    /** เก็บรหัสช่องทางไว้ด้วย เผื่อ Admin แก้หรือปิดช่องทางนั้นทีหลัง */
    @Column(name = "payment_method_code")
    private String paymentMethodCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    private SalePaymentType type;

    private BigDecimal amount;

    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_payment_id")
    private SalePayment reversesPayment;

    @Column(name = "created_at")
    private Instant createdAt;

    protected SalePayment() {}

    private SalePayment(Sale sale, PaymentMethod paymentMethod, SalePaymentType type,
                        BigDecimal amount, String reason, SalePayment reversesPayment) {
        this.sale = sale;
        this.paymentMethod = paymentMethod;
        this.paymentMethodCode = paymentMethod.getCode();
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.reversesPayment = reversesPayment;
        this.createdAt = Instant.now();
    }

    public static SalePayment received(Sale sale, PaymentMethod method, BigDecimal amount) {
        return new SalePayment(sale, method, SalePaymentType.PAYMENT, amount, null, null);
    }

    /** คืนเงินตามรายการรับเงินเดิม รายการเดิมยังอยู่ในประวัติเสมอ */
    public static SalePayment refundOf(SalePayment original, String reason) {
        return new SalePayment(original.getSale(), original.getPaymentMethod(), SalePaymentType.REFUND,
                original.getAmount().negate(), reason, original);
    }

    public Long getId() { return id; }
    public Sale getSale() { return sale; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getPaymentMethodCode() { return paymentMethodCode; }
    public SalePaymentType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public SalePayment getReversesPayment() { return reversesPayment; }
    public Instant getCreatedAt() { return createdAt; }
}
