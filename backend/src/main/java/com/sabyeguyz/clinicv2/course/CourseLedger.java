package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.sale.TransactionCancellation;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * Running balance of Course entitlements - a movement log, never an editable
 * "remaining" number. Each entry type names its source, and the database
 * rejects any other combination: Purchase needs a sales transaction,
 * Treatment a visit usage, TransferOut/TransferIn a transfer, Adjustment a
 * remark plus an approver, Reversal the ledger entry it reverses.
 */
@Entity
@Table(name = "course_ledger")
public class CourseLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    /** Patient whose balance changed. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "entry_type", nullable = false, columnDefinition = "entry_type_enum")
    private CourseLedgerEntryType entryType;

    /** +/- visit quantity change. */
    @Column(name = "qty_change", nullable = false)
    private int qtyChange;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(length = 255)
    private String remark;

    @Column(name = "ledger_create_at", nullable = false, updatable = false)
    private Instant ledgerCreateAt;

    @Column(name = "ledger_create_by", nullable = false, updatable = false)
    private Long ledgerCreateBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private CourseTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_usage_id")
    private CourseVisitUsage visitUsage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_ledger_id")
    private CourseLedger reversesLedger;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_transaction_id")
    private SalesTransaction salesTransaction;

    /** Cancellation entries (entitlement cut by a refund): exactly one of the refund item or the cancellation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_refund_item_id")
    private CourseRefundItem courseRefundItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancellation_id")
    private TransactionCancellation cancellation;

    @Column(name = "adjustment_approved_by")
    private Long adjustmentApprovedBy;

    @Column(name = "adjustment_approved_at")
    private Instant adjustmentApprovedAt;

    public CourseLedger() {}

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public CourseLedgerEntryType getEntryType() { return entryType; }
    public void setEntryType(CourseLedgerEntryType entryType) { this.entryType = entryType; }
    public int getQtyChange() { return qtyChange; }
    public void setQtyChange(int qtyChange) { this.qtyChange = qtyChange; }
    public int getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(int balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Instant getLedgerCreateAt() { return ledgerCreateAt; }
    public void setLedgerCreateAt(Instant ledgerCreateAt) { this.ledgerCreateAt = ledgerCreateAt; }
    public Long getLedgerCreateBy() { return ledgerCreateBy; }
    public void setLedgerCreateBy(Long ledgerCreateBy) { this.ledgerCreateBy = ledgerCreateBy; }
    public CourseTransfer getTransfer() { return transfer; }
    public void setTransfer(CourseTransfer transfer) { this.transfer = transfer; }
    public CourseVisitUsage getVisitUsage() { return visitUsage; }
    public void setVisitUsage(CourseVisitUsage visitUsage) { this.visitUsage = visitUsage; }
    public CourseLedger getReversesLedger() { return reversesLedger; }
    public void setReversesLedger(CourseLedger reversesLedger) { this.reversesLedger = reversesLedger; }
    public Instant getEventAt() { return eventAt; }
    public void setEventAt(Instant eventAt) { this.eventAt = eventAt; }
    public SalesTransaction getSalesTransaction() { return salesTransaction; }
    public void setSalesTransaction(SalesTransaction salesTransaction) { this.salesTransaction = salesTransaction; }
    public CourseRefundItem getCourseRefundItem() { return courseRefundItem; }
    public void setCourseRefundItem(CourseRefundItem courseRefundItem) { this.courseRefundItem = courseRefundItem; }
    public TransactionCancellation getCancellation() { return cancellation; }
    public void setCancellation(TransactionCancellation cancellation) { this.cancellation = cancellation; }
    public Long getAdjustmentApprovedBy() { return adjustmentApprovedBy; }
    public void setAdjustmentApprovedBy(Long adjustmentApprovedBy) { this.adjustmentApprovedBy = adjustmentApprovedBy; }
    public Instant getAdjustmentApprovedAt() { return adjustmentApprovedAt; }
    public void setAdjustmentApprovedAt(Instant adjustmentApprovedAt) { this.adjustmentApprovedAt = adjustmentApprovedAt; }
}
