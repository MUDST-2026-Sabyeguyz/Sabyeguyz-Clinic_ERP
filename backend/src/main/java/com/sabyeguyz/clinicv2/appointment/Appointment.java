package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.resource.Room;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "appointment")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** Provider / physiotherapist assigned. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private Service service;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "appointment_status", nullable = false)
    private AppointmentStatus appointmentStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time", nullable = false)
    private LocalTime appointmentTime;

    @Column(name = "appointment_create_at", nullable = false, updatable = false)
    private Instant appointmentCreateAt;

    @Column(name = "appointment_create_by", nullable = false, updatable = false)
    private Long appointmentCreateBy;

    @Column(name = "appointment_update_at")
    private Instant appointmentUpdateAt;

    @Column(name = "appointment_update_by")
    private Long appointmentUpdateBy;

    public Appointment() {}

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }
    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
    public AppointmentStatus getAppointmentStatus() { return appointmentStatus; }
    public void setAppointmentStatus(AppointmentStatus appointmentStatus) { this.appointmentStatus = appointmentStatus; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public LocalTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalTime appointmentTime) { this.appointmentTime = appointmentTime; }
    public Instant getAppointmentCreateAt() { return appointmentCreateAt; }
    public void setAppointmentCreateAt(Instant appointmentCreateAt) { this.appointmentCreateAt = appointmentCreateAt; }
    public Long getAppointmentCreateBy() { return appointmentCreateBy; }
    public void setAppointmentCreateBy(Long appointmentCreateBy) { this.appointmentCreateBy = appointmentCreateBy; }
    public Instant getAppointmentUpdateAt() { return appointmentUpdateAt; }
    public void setAppointmentUpdateAt(Instant appointmentUpdateAt) { this.appointmentUpdateAt = appointmentUpdateAt; }
    public Long getAppointmentUpdateBy() { return appointmentUpdateBy; }
    public void setAppointmentUpdateBy(Long appointmentUpdateBy) { this.appointmentUpdateBy = appointmentUpdateBy; }
}
