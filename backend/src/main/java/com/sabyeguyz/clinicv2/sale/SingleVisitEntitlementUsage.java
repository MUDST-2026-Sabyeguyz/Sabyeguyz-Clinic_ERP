package com.sabyeguyz.clinicv2.sale;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.sabyeguyz.clinicv2.appointment.Appointment;
import com.sabyeguyz.clinicv2.appointment.PatientVisit;

/** Append-only redemption history; a reversal restores one prepaid service without deleting its history. */
@Entity
@Table(name = "single_visit_entitlement_usage", uniqueConstraints =
        @UniqueConstraint(name = "uq_single_visit_entitlement_appointment", columnNames = "appointment_id"))
public class SingleVisitEntitlementUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "single_visit_entitlement_usage_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "single_visit_entitlement_id", nullable = false)
    private SingleVisitEntitlement entitlement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
    private PatientVisit visit;

    @Column(name = "used_at", nullable = false, updatable = false)
    private Instant usedAt;

    @Column(name = "used_by", nullable = false, updatable = false)
    private Long usedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversal_reason", columnDefinition = "text")
    private String reversalReason;

    public SingleVisitEntitlementUsage() {}

    public Long getId() { return id; }

    public SingleVisitEntitlement getEntitlement() { return entitlement; }
    public void setEntitlement(SingleVisitEntitlement entitlement) { this.entitlement = entitlement; }

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }

    public PatientVisit getVisit() { return visit; }
    public void setVisit(PatientVisit visit) { this.visit = visit; }

    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }

    public Long getUsedBy() { return usedBy; }
    public void setUsedBy(Long usedBy) { this.usedBy = usedBy; }

    public Instant getReversedAt() { return reversedAt; }
    public void setReversedAt(Instant reversedAt) { this.reversedAt = reversedAt; }

    public Long getReversedBy() { return reversedBy; }
    public void setReversedBy(Long reversedBy) { this.reversedBy = reversedBy; }

    public String getReversalReason() { return reversalReason; }
    public void setReversalReason(String reversalReason) { this.reversalReason = reversalReason; }
}
