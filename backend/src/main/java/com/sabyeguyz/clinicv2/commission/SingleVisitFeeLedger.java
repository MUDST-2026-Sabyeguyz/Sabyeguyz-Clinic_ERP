package com.sabyeguyz.clinicv2.commission;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.enums.AllocationEntryType;
import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.staff.Staff;

/** Treatment fee earned by the treating PT for a single (non-course) visit. An Allocation is never edited: a correction before payment is a Reversal plus a new Allocation of the next version. */
@Entity
@Table(name = "single_visit_fee_ledger")
public class SingleVisitFeeLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fee_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id")
    private PatientVisit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_fee_rule_id")
    private TreatmentFeeRule treatmentFeeRule;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "fee_type", nullable = false, columnDefinition = "fee_type_enum")
    private FeeType feeType;

    @Column(name = "fee_value", precision = 12, scale = 4, nullable = false)
    private BigDecimal feeValue;

    /** Net amount of the single-visit line (after discounts); kept for audit also for Fixed rules. */
    @Column(name = "calculation_base", precision = 12, scale = 2, nullable = false)
    private BigDecimal calculationBase;

    @Column(name = "fee_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal feeAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "entry_type", nullable = false, columnDefinition = "compensation_entry_type_enum")
    private AllocationEntryType entryType = AllocationEntryType.Allocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_fee_id")
    private SingleVisitFeeLedger reversesFee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_payment_id")
    private StaffMonthlyPayment monthlyPayment;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "fee_version", nullable = false)
    private int feeVersion = 1;

    public SingleVisitFeeLedger() {}

    public Long getId() { return id; }
    public PatientVisit getVisit() { return visit; }
    public void setVisit(PatientVisit visit) { this.visit = visit; }
    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }
    public TreatmentFeeRule getTreatmentFeeRule() { return treatmentFeeRule; }
    public void setTreatmentFeeRule(TreatmentFeeRule treatmentFeeRule) { this.treatmentFeeRule = treatmentFeeRule; }
    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }
    public FeeType getFeeType() { return feeType; }
    public void setFeeType(FeeType feeType) { this.feeType = feeType; }
    public BigDecimal getFeeValue() { return feeValue; }
    public void setFeeValue(BigDecimal feeValue) { this.feeValue = feeValue; }
    public BigDecimal getCalculationBase() { return calculationBase; }
    public void setCalculationBase(BigDecimal calculationBase) { this.calculationBase = calculationBase; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public AllocationEntryType getEntryType() { return entryType; }
    public void setEntryType(AllocationEntryType entryType) { this.entryType = entryType; }
    public SingleVisitFeeLedger getReversesFee() { return reversesFee; }
    public void setReversesFee(SingleVisitFeeLedger reversesFee) { this.reversesFee = reversesFee; }
    public StaffMonthlyPayment getMonthlyPayment() { return monthlyPayment; }
    public void setMonthlyPayment(StaffMonthlyPayment monthlyPayment) { this.monthlyPayment = monthlyPayment; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public int getFeeVersion() { return feeVersion; }
    public void setFeeVersion(int feeVersion) { this.feeVersion = feeVersion; }
}
