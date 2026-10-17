package com.sabyeguyz.clinic.sale;

import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.patient.Patient;
import com.sabyeguyz.clinic.staff.Staff;
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
 * หัวใบเสร็จหนึ่งใบ ยอดรวมคำนวณจากราคาใน master ตอนขาย ไม่ได้รับมาจากผู้เรียก
 * ยอดเงินที่รับจริงไม่ได้อยู่ในตารางนี้ — คำนวณจากผลรวมของ {@link SalePayment}
 */
@Entity
@Table(name = "sales")
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sale_id_generator")
    @SequenceGenerator(name = "sale_id_generator", sequenceName = "sale_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "receipt_no")
    private String receiptNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** ผู้ขาย อาจไม่ใช่คนเดียวกับผู้รักษา จึงเก็บแยกจาก treatments */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesperson_staff_id")
    private Staff salesperson;

    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private SaleStatus status;

    private String note;

    @Column(name = "sold_at")
    private Instant soldAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Sale() {}

    public Sale(String receiptNo, Patient patient, Branch branch, Staff salesperson,
                BigDecimal total, String note, Instant soldAt) {
        this.receiptNo = receiptNo;
        this.patient = patient;
        this.branch = branch;
        this.salesperson = salesperson;
        this.total = total;
        this.note = note;
        this.soldAt = soldAt;
        this.status = SaleStatus.PAID;
        this.createdAt = Instant.now();
    }

    /**
     * ปิดใบเสร็จโดยไม่ลบอะไรเลย ตัวเลขรายได้ถูกกลับรายการผ่าน {@link SalePayment} แยกต่างหาก
     * ที่นี่เก็บแค่ว่าใครยกเลิกเมื่อไรและเพราะอะไร
     */
    public void voidSale(String reason, Instant when) {
        this.status = SaleStatus.VOIDED;
        this.voidReason = reason;
        this.voidedAt = when;
    }

    public Long getId() { return id; }
    public String getReceiptNo() { return receiptNo; }
    public Patient getPatient() { return patient; }
    public Branch getBranch() { return branch; }
    public Staff getSalesperson() { return salesperson; }
    public BigDecimal getTotal() { return total; }
    public SaleStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Instant getSoldAt() { return soldAt; }
    public Instant getVoidedAt() { return voidedAt; }
    public String getVoidReason() { return voidReason; }
    public Instant getCreatedAt() { return createdAt; }
}
