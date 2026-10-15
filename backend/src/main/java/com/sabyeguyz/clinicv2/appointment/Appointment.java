package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.enums.AppointmentPatientType;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.enums.CalendarSyncStatus;
import com.sabyeguyz.clinicv2.enums.RequestedServiceType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.resource.Room;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A booked slot. A NEW patient is not registered yet, so patient and service
 * stay empty and contact_name/contact_phone identify them; CURRENT requires a
 * patient (enforced by the database check as well as AppointmentService).
 */
@Entity
@Table(name = "appointment")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** Provider / physiotherapist assigned. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "appointment_status", nullable = false, columnDefinition = "appointment_status_enum")
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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "appointment_patient_type", nullable = false, columnDefinition = "appointment_patient_type_enum")
    private AppointmentPatientType appointmentPatientType = AppointmentPatientType.CURRENT;

    /** NEW: name, or nickname with phone; CURRENT: taken from the Patient. */
    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "requested_service_type", columnDefinition = "requested_service_type_enum")
    private RequestedServiceType requestedServiceType;

    /** Google Calendar link, written by {@code CalendarSyncService} (see app.calendar.* settings). */
    @Column(name = "external_calendar_event_id", length = 255)
    private String externalCalendarEventId;

    /** When this appointment was last in step with its calendar event; older than the update time means "dirty". */
    @Column(name = "calendar_last_synced_at")
    private Instant calendarLastSyncedAt;

    /** Etag Google returned for the event we last wrote or accepted; an event with this etag has no new change. */
    @Column(name = "calendar_event_etag", length = 255)
    private String calendarEventEtag;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "calendar_sync_status", columnDefinition = "calendar_sync_status_enum")
    private CalendarSyncStatus calendarSyncStatus;

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
    public AppointmentPatientType getAppointmentPatientType() { return appointmentPatientType; }
    public void setAppointmentPatientType(AppointmentPatientType appointmentPatientType) { this.appointmentPatientType = appointmentPatientType; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public RequestedServiceType getRequestedServiceType() { return requestedServiceType; }
    public void setRequestedServiceType(RequestedServiceType requestedServiceType) { this.requestedServiceType = requestedServiceType; }
    public String getExternalCalendarEventId() { return externalCalendarEventId; }
    public void setExternalCalendarEventId(String externalCalendarEventId) { this.externalCalendarEventId = externalCalendarEventId; }
    public CalendarSyncStatus getCalendarSyncStatus() { return calendarSyncStatus; }
    public void setCalendarSyncStatus(CalendarSyncStatus calendarSyncStatus) { this.calendarSyncStatus = calendarSyncStatus; }
    public Instant getCalendarLastSyncedAt() { return calendarLastSyncedAt; }
    public void setCalendarLastSyncedAt(Instant calendarLastSyncedAt) { this.calendarLastSyncedAt = calendarLastSyncedAt; }
    public String getCalendarEventEtag() { return calendarEventEtag; }
    public void setCalendarEventEtag(String calendarEventEtag) { this.calendarEventEtag = calendarEventEtag; }
}
