package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.course.CourseVisitUsage;
import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.enums.OverflowPolicy;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
    @JoinColumn(name = "case_owner_staff_id")
    private Staff caseOwnerStaff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treating_staff_id")
    private Staff treatingStaff;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "gross_commission_allocation", precision = 12, scale = 2, nullable = false)
    private BigDecimal grossCommissionAllocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_fee_rule_id")
    private TreatmentFeeRule treatmentFeeRule;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "treatment_fee_type")
    private FeeType treatmentFeeType;

    @Column(name = "treatment_fee_rate_or_amount", precision = 12, scale = 4)
    private BigDecimal treatmentFeeRateOrAmount;

    @Column(name = "treatment_fee_calculation_base", length = 50)
    private String treatmentFeeCalculationBase;

    @Column(name = "treatment_fee_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal treatmentFeeAmount;

    @Column(name = "owner_net_commission", precision = 12, scale = 2, nullable = false)
    private BigDecimal ownerNetCommission;

    /** Extra amount the company tops up under the COMPANY_TOP_UP overflow policy. */
    @Column(name = "company_top_up_amount", precision = 12, scale = 2)
    private BigDecimal companyTopUpAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "overflow_policy_used")
    private OverflowPolicy overflowPolicyUsed;

    /** Prior ledger entry this row adjusts, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_reference_id")
    private CommissionAllocationLedger adjustmentReference;

    @Column(name = "allocation_create_at", nullable = false, updatable = false)
    private Instant allocationCreateAt;

    @Column(name = "allocation_create_by", nullable = false, updatable = false)
    private Long allocationCreateBy;

    @Column(name = "allocation_update_at")
    private Instant allocationUpdateAt;

    @Column(name = "allocation_update_by")
    private Long allocationUpdateBy;

    protected CommissionAllocationLedger() {}

    public Long getId() { return id; }
    public CourseVisitUsage getVisitUsage() { return visitUsage; }
    public void setVisitUsage(CourseVisitUsage visitUsage) { this.visitUsage = visitUsage; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Staff getCaseOwnerStaff() { return caseOwnerStaff; }
    public void setCaseOwnerStaff(Staff caseOwnerStaff) { this.caseOwnerStaff = caseOwnerStaff; }
    public Staff getTreatingStaff() { return treatingStaff; }
    public void setTreatingStaff(Staff treatingStaff) { this.treatingStaff = treatingStaff; }
    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }
    public BigDecimal getGrossCommissionAllocation() { return grossCommissionAllocation; }
    public void setGrossCommissionAllocation(BigDecimal grossCommissionAllocation) { this.grossCommissionAllocation = grossCommissionAllocation; }
    public TreatmentFeeRule getTreatmentFeeRule() { return treatmentFeeRule; }
    public void setTreatmentFeeRule(TreatmentFeeRule treatmentFeeRule) { this.treatmentFeeRule = treatmentFeeRule; }
    public FeeType getTreatmentFeeType() { return treatmentFeeType; }
    public void setTreatmentFeeType(FeeType treatmentFeeType) { this.treatmentFeeType = treatmentFeeType; }
    public BigDecimal getTreatmentFeeRateOrAmount() { return treatmentFeeRateOrAmount; }
    public void setTreatmentFeeRateOrAmount(BigDecimal treatmentFeeRateOrAmount) { this.treatmentFeeRateOrAmount = treatmentFeeRateOrAmount; }
    public String getTreatmentFeeCalculationBase() { return treatmentFeeCalculationBase; }
    public void setTreatmentFeeCalculationBase(String treatmentFeeCalculationBase) { this.treatmentFeeCalculationBase = treatmentFeeCalculationBase; }
    public BigDecimal getTreatmentFeeAmount() { return treatmentFeeAmount; }
    public void setTreatmentFeeAmount(BigDecimal treatmentFeeAmount) { this.treatmentFeeAmount = treatmentFeeAmount; }
    public BigDecimal getOwnerNetCommission() { return ownerNetCommission; }
    public void setOwnerNetCommission(BigDecimal ownerNetCommission) { this.ownerNetCommission = ownerNetCommission; }
    public BigDecimal getCompanyTopUpAmount() { return companyTopUpAmount; }
    public void setCompanyTopUpAmount(BigDecimal companyTopUpAmount) { this.companyTopUpAmount = companyTopUpAmount; }
    public OverflowPolicy getOverflowPolicyUsed() { return overflowPolicyUsed; }
    public void setOverflowPolicyUsed(OverflowPolicy overflowPolicyUsed) { this.overflowPolicyUsed = overflowPolicyUsed; }
    public CommissionAllocationLedger getAdjustmentReference() { return adjustmentReference; }
    public void setAdjustmentReference(CommissionAllocationLedger adjustmentReference) { this.adjustmentReference = adjustmentReference; }
    public Instant getAllocationCreateAt() { return allocationCreateAt; }
    public void setAllocationCreateAt(Instant allocationCreateAt) { this.allocationCreateAt = allocationCreateAt; }
    public Long getAllocationCreateBy() { return allocationCreateBy; }
    public void setAllocationCreateBy(Long allocationCreateBy) { this.allocationCreateBy = allocationCreateBy; }
    public Instant getAllocationUpdateAt() { return allocationUpdateAt; }
    public void setAllocationUpdateAt(Instant allocationUpdateAt) { this.allocationUpdateAt = allocationUpdateAt; }
    public Long getAllocationUpdateBy() { return allocationUpdateBy; }
    public void setAllocationUpdateBy(Long allocationUpdateBy) { this.allocationUpdateBy = allocationUpdateBy; }
}
