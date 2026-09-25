package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.VisitStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/** Actual clinic attendance (as opposed to the booked {@link Appointment}). */
@Entity
@Table(name = "patient_visit")
public class PatientVisit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visit_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** Physiotherapist assigned at check-in (Walk-in/Changing). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private Staff assignedStaff;

    @Column(name = "visit_date", nullable = false)
    private Instant visitDate;

    @Column(name = "checkout_time")
    private Instant checkoutTime;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "visit_status", nullable = false)
    private VisitStatus visitStatus;

    @Column(name = "visit_create_at", nullable = false, updatable = false)
    private Instant visitCreateAt;

    @Column(name = "visit_create_by", nullable = false, updatable = false)
    private Long visitCreateBy;

    @Column(name = "visit_update_at")
    private Instant visitUpdateAt;

    @Column(name = "visit_update_by")
    private Long visitUpdateBy;

    public PatientVisit() {}

    public Long getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public Staff getAssignedStaff() { return assignedStaff; }
    public void setAssignedStaff(Staff assignedStaff) { this.assignedStaff = assignedStaff; }
    public Instant getVisitDate() { return visitDate; }
    public void setVisitDate(Instant visitDate) { this.visitDate = visitDate; }
    public Instant getCheckoutTime() { return checkoutTime; }
    public void setCheckoutTime(Instant checkoutTime) { this.checkoutTime = checkoutTime; }
    public VisitStatus getVisitStatus() { return visitStatus; }
    public void setVisitStatus(VisitStatus visitStatus) { this.visitStatus = visitStatus; }
    public Instant getVisitCreateAt() { return visitCreateAt; }
    public void setVisitCreateAt(Instant visitCreateAt) { this.visitCreateAt = visitCreateAt; }
    public Long getVisitCreateBy() { return visitCreateBy; }
    public void setVisitCreateBy(Long visitCreateBy) { this.visitCreateBy = visitCreateBy; }
    public Instant getVisitUpdateAt() { return visitUpdateAt; }
    public void setVisitUpdateAt(Instant visitUpdateAt) { this.visitUpdateAt = visitUpdateAt; }
    public Long getVisitUpdateBy() { return visitUpdateBy; }
    public void setVisitUpdateBy(Long visitUpdateBy) { this.visitUpdateBy = visitUpdateBy; }
}
