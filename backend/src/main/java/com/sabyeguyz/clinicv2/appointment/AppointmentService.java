package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.resource.Room;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** Appointment booking (Clinic ERP Workflow Brief §4). */
@org.springframework.stereotype.Service
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final AppointmentStatusHistoryRepository history;

    public AppointmentService(AppointmentRepository appointments, AppointmentStatusHistoryRepository history) {
        this.appointments = appointments;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<Appointment> list() {
        return appointments.findAll();
    }

    @Transactional(readOnly = true)
    public Appointment get(Long id) {
        return find(id);
    }

    @Transactional
    public Appointment book(Patient patient, Branch branch, Staff staff, Service service, Room room,
                             LocalDate appointmentDate, LocalTime appointmentTime, Long actorId) {
        require(patient != null, "ต้องระบุผู้ป่วย");
        require(branch != null, "ต้องระบุสาขา");
        require(staff != null, "ต้องระบุผู้ให้บริการ");
        require(service != null, "ต้องระบุบริการ");
        require(appointmentDate != null, "ต้องระบุวันที่นัด");
        require(appointmentTime != null, "ต้องระบุเวลานัด");
        requireNoConflict(staff.getId(), appointmentDate, appointmentTime, null);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setBranch(branch);
        appointment.setStaff(staff);
        appointment.setService(service);
        appointment.setRoom(room);
        appointment.setAppointmentDate(appointmentDate);
        appointment.setAppointmentTime(appointmentTime);
        appointment.setAppointmentStatus(AppointmentStatus.Confirmed);
        appointment.setAppointmentCreateAt(Instant.now());
        appointment.setAppointmentCreateBy(actorId);
        return appointments.save(appointment);
    }

    /** Moves the same appointment to a new date/time; does not create a new row. */
    @Transactional
    public Appointment reschedule(Long id, LocalDate newDate, LocalTime newTime, String reason, Long actorId) {
        Appointment appointment = find(id);
        requireNotTerminal(appointment);
        require(newDate != null, "ต้องระบุวันที่นัดใหม่");
        require(newTime != null, "ต้องระบุเวลานัดใหม่");
        requireNoConflict(appointment.getStaff().getId(), newDate, newTime, id);

        appointment.setAppointmentDate(newDate);
        appointment.setAppointmentTime(newTime);
        changeStatus(appointment, AppointmentStatus.Confirmed, reason, actorId);
        appointment.setAppointmentUpdateAt(Instant.now());
        appointment.setAppointmentUpdateBy(actorId);
        return appointments.save(appointment);
    }

    @Transactional
    public Appointment cancel(Long id, String reason, Long actorId) {
        Appointment appointment = find(id);
        requireNotTerminal(appointment);
        require(reason != null && !reason.isBlank(), "ต้องระบุเหตุผลที่ยกเลิก");
        changeStatus(appointment, AppointmentStatus.Cancelled, reason, actorId);
        appointment.setAppointmentUpdateAt(Instant.now());
        appointment.setAppointmentUpdateBy(actorId);
        return appointments.save(appointment);
    }

    @Transactional
    public Appointment markNoShow(Long id, Long actorId) {
        Appointment appointment = find(id);
        requireNotTerminal(appointment);
        changeStatus(appointment, AppointmentStatus.NoShow, "ไม่มาตามนัด", actorId);
        appointment.setAppointmentUpdateAt(Instant.now());
        appointment.setAppointmentUpdateBy(actorId);
        return appointments.save(appointment);
    }

    /** Called by {@code PatientVisitService} when the linked visit progresses; kept as the single writer of Appointment status. */
    @Transactional
    public Appointment markArrived(Long id, Long actorId) {
        return transition(id, AppointmentStatus.Arrived, "ผู้ป่วยมาถึงคลินิก", actorId);
    }

    @Transactional
    public Appointment markInService(Long id, Long actorId) {
        return transition(id, AppointmentStatus.InService, "เข้ารับบริการ", actorId);
    }

    @Transactional
    public Appointment markCompleted(Long id, Long actorId) {
        return transition(id, AppointmentStatus.Completed, "เสร็จสิ้นบริการ", actorId);
    }

    private Appointment transition(Long id, AppointmentStatus newStatus, String reason, Long actorId) {
        Appointment appointment = find(id);
        requireNotTerminal(appointment);
        changeStatus(appointment, newStatus, reason, actorId);
        appointment.setAppointmentUpdateAt(Instant.now());
        appointment.setAppointmentUpdateBy(actorId);
        return appointments.save(appointment);
    }

    /** Applies the new status and writes an Appointment_status_history row (audit trail). */
    private void changeStatus(Appointment appointment, AppointmentStatus newStatus, String reason, Long actorId) {
        AppointmentStatus previous = appointment.getAppointmentStatus();
        appointment.setAppointmentStatus(newStatus);
        AppointmentStatusHistory entry = new AppointmentStatusHistory();
        entry.setAppointment(appointment);
        entry.setFromStatus(previous);
        entry.setToStatus(newStatus);
        entry.setChangedBy(actorId);
        entry.setChangedAt(Instant.now());
        entry.setReason(reason);
        history.save(entry);
    }

    private void requireNoConflict(Long staffId, LocalDate date, LocalTime time, Long excludingAppointmentId) {
        boolean conflict = appointments.existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn(
                staffId, date, time, AppointmentStatus.slotReleasing());
        if (conflict) {
            throw new IllegalArgumentException("ผู้ให้บริการไม่ว่างในวันเวลาที่เลือก");
        }
    }

    private static void requireNotTerminal(Appointment appointment) {
        AppointmentStatus status = appointment.getAppointmentStatus();
        if (status == AppointmentStatus.Completed || status == AppointmentStatus.Cancelled
                || status == AppointmentStatus.NoShow) {
            throw new IllegalArgumentException("นัดหมายนี้จบสถานะแล้ว ไม่สามารถแก้ไขได้อีก");
        }
    }

    private Appointment find(Long id) {
        return appointments.findById(id).orElseThrow(AppointmentNotFoundException::new);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
