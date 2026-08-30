package com.sabyeguyz.clinic.appointment;

import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.patient.Patient;
import com.sabyeguyz.clinic.catalog.ServiceItem;
import com.sabyeguyz.clinic.therapist.Therapist;
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
import java.time.Instant;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "appointment_id_generator")
    @SequenceGenerator(name = "appointment_id_generator", sequenceName = "appointment_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "therapist_id")
    private Therapist therapist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private ServiceItem service;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private AppointmentStatus status;

    private String note;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Appointment() {}

    public Appointment(Branch branch, Patient patient, Therapist therapist, ServiceItem service,
                       Instant startAt, Instant endAt, String note) {
        this.branch = branch;
        this.patient = patient;
        this.therapist = therapist;
        this.service = service;
        this.startAt = startAt;
        this.endAt = endAt;
        this.note = note;
        this.status = AppointmentStatus.SCHEDULED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    //method for testing /api/appointments/{id}/cancel
    public void cancel(String reason) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Branch getBranch() { return branch; }
    public Patient getPatient() { return patient; }
    public Therapist getTherapist() { return therapist; }
    public ServiceItem getService() { return service; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public AppointmentStatus getStatus() { return status; }
    public String getNote() { return note; }
    public String getCancelReason() { return cancelReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

}
