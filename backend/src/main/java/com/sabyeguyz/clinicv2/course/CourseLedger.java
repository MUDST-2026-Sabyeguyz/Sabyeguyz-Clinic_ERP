package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.patient.Patient;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/** Running balance of Course entitlements — a movement log, never an editable "remaining" number. */
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
    @Column(name = "entry_type", nullable = false)
    private CourseLedgerEntryType entryType;

    /** +/- visit quantity change. */
    @Column(name = "qty_change", nullable = false)
    private int qtyChange;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    /** e.g. VISIT, TRANSFER. */
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(length = 255)
    private String remark;

    @Column(name = "ledger_create_at", nullable = false, updatable = false)
    private Instant ledgerCreateAt;

    @Column(name = "ledger_create_by", nullable = false, updatable = false)
    private Long ledgerCreateBy;

    @Column(name = "ledger_update_at")
    private Instant ledgerUpdateAt;

    @Column(name = "ledger_update_by")
    private Long ledgerUpdateBy;

    protected CourseLedger() {}

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
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Instant getLedgerCreateAt() { return ledgerCreateAt; }
    public void setLedgerCreateAt(Instant ledgerCreateAt) { this.ledgerCreateAt = ledgerCreateAt; }
    public Long getLedgerCreateBy() { return ledgerCreateBy; }
    public void setLedgerCreateBy(Long ledgerCreateBy) { this.ledgerCreateBy = ledgerCreateBy; }
    public Instant getLedgerUpdateAt() { return ledgerUpdateAt; }
    public void setLedgerUpdateAt(Instant ledgerUpdateAt) { this.ledgerUpdateAt = ledgerUpdateAt; }
    public Long getLedgerUpdateBy() { return ledgerUpdateBy; }
    public void setLedgerUpdateBy(Long ledgerUpdateBy) { this.ledgerUpdateBy = ledgerUpdateBy; }
}
