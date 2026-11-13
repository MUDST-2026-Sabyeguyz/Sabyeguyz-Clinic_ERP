package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.course.CourseVisitUsage;
import com.sabyeguyz.clinicv2.enums.AllocationEntryType;
import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;

/** Appendix A of Commission_System_Requirement_Dev: the audit trail of how each visit's gross allocation was split. */
@Entity
@Table(name = "commission_allocation_ledger")
public class CommissionAllocationLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_usage_id")
    private CourseVisitUsage visitUsage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_staff_id")
    private Staff ownerStaff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treating_staff_id")
    private Staff treatingStaff;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_fee_rule_id")
    private TreatmentFeeRule treatmentFeeRule;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "treatment_fee_type", columnDefinition = "fee_type_enum")
    private FeeType treatmentFeeType;

    @Column(name = "treatment_fee_rate_or_amount", precision = 12, scale = 4)
    private BigDecimal treatmentFeeRateOrAmount;

    @Column(name = "treatment_fee_calculation_base", precision = 12, scale = 4)
    private BigDecimal treatmentFeeCalculationBase;

    @Column(name = "treatment_fee_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal treatmentFeeAmount;

    @Column(name = "owner_net_commission", precision = 12, scale = 2, nullable = false)
    private BigDecimal ownerNetCommission;

    /** The Allocation this Reversal undoes (one Reversal per Allocation). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_allocation_id")
    private CommissionAllocationLedger reversesAllocation;

    /** Version of the allocation of one usage; a Reversal carries the version of the allocation it reverses. */
    @Column(name = "allocation_version", nullable = false)
    private int allocationVersion = 1;

    @Column(name = "allocation_create_at", nullable = false, updatable = false)
    private Instant allocationCreateAt;

    @Column(name = "allocation_create_by", nullable = false, updatable = false)
    private Long allocationCreateBy;

    @Column(name = "allocation_update_at")
    private Instant allocationUpdateAt;

    @Column(name = "allocation_update_by")
    private Long allocationUpdateBy;

    /** Allocation books the commission; Reversal (negative amounts) undoes one Allocation before the month is paid. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "entry_type", nullable = false, columnDefinition = "compensation_entry_type_enum")
    private AllocationEntryType entryType = AllocationEntryType.Allocation;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    /** Payout of the Owner (staff must equal owner_staff_id); NULL until the month is arranged. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_monthly_payment_id")
    private StaffMonthlyPayment ownerMonthlyPayment;

    /** Payout of the treating PT for the treatment fee (staff must equal treating_staff_id). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treating_monthly_payment_id")
    private StaffMonthlyPayment treatingMonthlyPayment;

    public CommissionAllocationLedger() {}

    public Long getId() { return id; }

    public CourseVisitUsage getVisitUsage() { return visitUsage; }
    public void setVisitUsage(CourseVisitUsage visitUsage) { this.visitUsage = visitUsage; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    public Staff getOwnerStaff() { return ownerStaff; }
    public void setOwnerStaff(Staff ownerStaff) { this.ownerStaff = ownerStaff; }

    public Staff getTreatingStaff() { return treatingStaff; }
    public void setTreatingStaff(Staff treatingStaff) { this.treatingStaff = treatingStaff; }

    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }

    public TreatmentFeeRule getTreatmentFeeRule() { return treatmentFeeRule; }
    public void setTreatmentFeeRule(TreatmentFeeRule treatmentFeeRule) { this.treatmentFeeRule = treatmentFeeRule; }

    public FeeType getTreatmentFeeType() { return treatmentFeeType; }
    public void setTreatmentFeeType(FeeType treatmentFeeType) { this.treatmentFeeType = treatmentFeeType; }

    public BigDecimal getTreatmentFeeRateOrAmount() { return treatmentFeeRateOrAmount; }
    public void setTreatmentFeeRateOrAmount(BigDecimal treatmentFeeRateOrAmount) { this.treatmentFeeRateOrAmount = treatmentFeeRateOrAmount; }

    public BigDecimal getTreatmentFeeCalculationBase() { return treatmentFeeCalculationBase; }
    public void setTreatmentFeeCalculationBase(BigDecimal treatmentFeeCalculationBase) { this.treatmentFeeCalculationBase = treatmentFeeCalculationBase; }

    public BigDecimal getTreatmentFeeAmount() { return treatmentFeeAmount; }
    public void setTreatmentFeeAmount(BigDecimal treatmentFeeAmount) { this.treatmentFeeAmount = treatmentFeeAmount; }

    public BigDecimal getOwnerNetCommission() { return ownerNetCommission; }
    public void setOwnerNetCommission(BigDecimal ownerNetCommission) { this.ownerNetCommission = ownerNetCommission; }

    public CommissionAllocationLedger getReversesAllocation() { return reversesAllocation; }
    public void setReversesAllocation(CommissionAllocationLedger reversesAllocation) { this.reversesAllocation = reversesAllocation; }

    public int getAllocationVersion() { return allocationVersion; }
    public void setAllocationVersion(int allocationVersion) { this.allocationVersion = allocationVersion; }

    public Instant getAllocationCreateAt() { return allocationCreateAt; }
    public void setAllocationCreateAt(Instant allocationCreateAt) { this.allocationCreateAt = allocationCreateAt; }

    public Long getAllocationCreateBy() { return allocationCreateBy; }
    public void setAllocationCreateBy(Long allocationCreateBy) { this.allocationCreateBy = allocationCreateBy; }

    public Instant getAllocationUpdateAt() { return allocationUpdateAt; }
    public void setAllocationUpdateAt(Instant allocationUpdateAt) { this.allocationUpdateAt = allocationUpdateAt; }

    public Long getAllocationUpdateBy() { return allocationUpdateBy; }
    public void setAllocationUpdateBy(Long allocationUpdateBy) { this.allocationUpdateBy = allocationUpdateBy; }

    public AllocationEntryType getEntryType() { return entryType; }
    public void setEntryType(AllocationEntryType entryType) { this.entryType = entryType; }

    public StaffMonthlyPayment getOwnerMonthlyPayment() { return ownerMonthlyPayment; }
    public void setOwnerMonthlyPayment(StaffMonthlyPayment ownerMonthlyPayment) { this.ownerMonthlyPayment = ownerMonthlyPayment; }

    public StaffMonthlyPayment getTreatingMonthlyPayment() { return treatingMonthlyPayment; }
    public void setTreatingMonthlyPayment(StaffMonthlyPayment treatingMonthlyPayment) { this.treatingMonthlyPayment = treatingMonthlyPayment; }

    public String getReversalReason() { return reversalReason; }
    public void setReversalReason(String reversalReason) { this.reversalReason = reversalReason; }
}
