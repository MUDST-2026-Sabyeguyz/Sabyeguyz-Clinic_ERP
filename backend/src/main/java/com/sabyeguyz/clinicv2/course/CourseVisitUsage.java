package com.sabyeguyz.clinicv2.course;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.commission.TreatmentFeeRule;
import com.sabyeguyz.clinicv2.enums.UsageStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.treatment.Treatment;

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

    @Column(name = "visit_date", nullable = false)
    private LocalDateTime visitDate;

    @Column(name = "visit_qty", nullable = false)
    private int visitQty = 1;

    /** Frozen per-visit rate copied from Course. */
    @Column(name = "commission_allocation_per_visit", precision = 12, scale = 2)
    private BigDecimal commissionAllocationPerVisit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_fee_rule_id")
    private TreatmentFeeRule treatmentFeeRule;

    @Column(name = "substitute_treatment_fee_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal substituteTreatmentFeeAmount = BigDecimal.ZERO;

    /** What the Owner earns for this usage: the per-visit amount when the Owner treated, 0 for a substitute. */
    @Column(name = "owner_net_commission", precision = 12, scale = 2)
    private BigDecimal ownerNetCommission;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "usage_status", nullable = false, columnDefinition = "usage_status_enum")
    private UsageStatus usageStatus = UsageStatus.Completed;

    /** Position of the usage inside its course, all users together; a Reversed position is reused by the next usage. */
    @Column(name = "usage_sequence", nullable = false)
    private int usageSequence;

    /** The Reversed usage (same course and position) this one replaces. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_usage_id")
    private CourseVisitUsage replacesUsage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_id")
    private Treatment treatment;

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

    /** The Owner PT of the usage is the course's seller. */
    public Staff getOwnerStaff() {
        return course.getOwnerStaff();
    }

    public LocalDateTime getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDateTime visitDate) { this.visitDate = visitDate; }

    public int getVisitQty() { return visitQty; }
    public void setVisitQty(int visitQty) { this.visitQty = visitQty; }

    public BigDecimal getCommissionAllocationPerVisit() { return commissionAllocationPerVisit; }
    public void setCommissionAllocationPerVisit(BigDecimal commissionAllocationPerVisit) { this.commissionAllocationPerVisit = commissionAllocationPerVisit; }

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

    public UsageStatus getUsageStatus() { return usageStatus; }
    public void setUsageStatus(UsageStatus usageStatus) { this.usageStatus = usageStatus; }

    public int getUsageSequence() { return usageSequence; }
    public void setUsageSequence(int usageSequence) { this.usageSequence = usageSequence; }

    public CourseVisitUsage getReplacesUsage() { return replacesUsage; }
    public void setReplacesUsage(CourseVisitUsage replacesUsage) { this.replacesUsage = replacesUsage; }

    public Treatment getTreatment() { return treatment; }
    public void setTreatment(Treatment treatment) { this.treatment = treatment; }
}
