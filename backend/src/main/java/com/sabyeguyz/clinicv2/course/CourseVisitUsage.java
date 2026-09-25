package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.commission.TreatmentFeeRule;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One use of a Course entitlement. Follows Commission_System_Requirement_Dev
 * Financial Allocation Rule: gross_commission_allocation is copied from the
 * frozen Course rate, then split between the substitute treatment fee and
 * the case owner's net commission.
 */
@Entity
@Table(name = "course_visit_usage")
public class CourseVisitUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visit_usage_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id")
    private PatientVisit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    /** Patient using the entitlement (owner or shared member). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treating_staff_id")
    private Staff treatingStaff;

    /** Commission pool owner, copied from Course — not re-selected by the user. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_owner_staff_id")
    private Staff caseOwnerStaff;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "visit_qty", nullable = false)
    private int visitQty = 1;

    /** Frozen per-visit rate copied from Course. */
    @Column(name = "commission_allocation_per_visit", precision = 12, scale = 2, nullable = false)
    private BigDecimal commissionAllocationPerVisit;

    @Column(name = "gross_commission_allocation", precision = 12, scale = 2, nullable = false)
    private BigDecimal grossCommissionAllocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_fee_rule_id")
    private TreatmentFeeRule treatmentFeeRule;

    @Column(name = "substitute_treatment_fee_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal substituteTreatmentFeeAmount = BigDecimal.ZERO;

    /** gross_commission_allocation - substitute_treatment_fee_amount. */
    @Column(name = "owner_net_commission", precision = 12, scale = 2, nullable = false)
    private BigDecimal ownerNetCommission;

    @Column(name = "usage_create_at", nullable = false, updatable = false)
    private Instant usageCreateAt;

    @Column(name = "usage_create_by", nullable = false, updatable = false)
    private Long usageCreateBy;

    @Column(name = "usage_update_at")
    private Instant usageUpdateAt;

    @Column(name = "usage_update_by")
    private Long usageUpdateBy;

    public CourseVisitUsage() {}

    public Long getId() { return id; }
    public PatientVisit getVisit() { return visit; }
    public void setVisit(PatientVisit visit) { this.visit = visit; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Staff getTreatingStaff() { return treatingStaff; }
    public void setTreatingStaff(Staff treatingStaff) { this.treatingStaff = treatingStaff; }
    public Staff getCaseOwnerStaff() { return caseOwnerStaff; }
    public void setCaseOwnerStaff(Staff caseOwnerStaff) { this.caseOwnerStaff = caseOwnerStaff; }
    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }
    public int getVisitQty() { return visitQty; }
    public void setVisitQty(int visitQty) { this.visitQty = visitQty; }
    public BigDecimal getCommissionAllocationPerVisit() { return commissionAllocationPerVisit; }
    public void setCommissionAllocationPerVisit(BigDecimal commissionAllocationPerVisit) { this.commissionAllocationPerVisit = commissionAllocationPerVisit; }
    public BigDecimal getGrossCommissionAllocation() { return grossCommissionAllocation; }
    public void setGrossCommissionAllocation(BigDecimal grossCommissionAllocation) { this.grossCommissionAllocation = grossCommissionAllocation; }
    public TreatmentFeeRule getTreatmentFeeRule() { return treatmentFeeRule; }
    public void setTreatmentFeeRule(TreatmentFeeRule treatmentFeeRule) { this.treatmentFeeRule = treatmentFeeRule; }
    public BigDecimal getSubstituteTreatmentFeeAmount() { return substituteTreatmentFeeAmount; }
    public void setSubstituteTreatmentFeeAmount(BigDecimal substituteTreatmentFeeAmount) { this.substituteTreatmentFeeAmount = substituteTreatmentFeeAmount; }
    public BigDecimal getOwnerNetCommission() { return ownerNetCommission; }
    public void setOwnerNetCommission(BigDecimal ownerNetCommission) { this.ownerNetCommission = ownerNetCommission; }
    public Instant getUsageCreateAt() { return usageCreateAt; }
    public void setUsageCreateAt(Instant usageCreateAt) { this.usageCreateAt = usageCreateAt; }
    public Long getUsageCreateBy() { return usageCreateBy; }
    public void setUsageCreateBy(Long usageCreateBy) { this.usageCreateBy = usageCreateBy; }
    public Instant getUsageUpdateAt() { return usageUpdateAt; }
    public void setUsageUpdateAt(Instant usageUpdateAt) { this.usageUpdateAt = usageUpdateAt; }
    public Long getUsageUpdateBy() { return usageUpdateBy; }
    public void setUsageUpdateBy(Long usageUpdateBy) { this.usageUpdateBy = usageUpdateBy; }
}
