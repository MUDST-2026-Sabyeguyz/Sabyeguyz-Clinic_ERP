package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.enums.AppointmentPatientType;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.enums.RequestedServiceType;
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
    public Appointment getForUpdate(Long id) {
        return appointments.findByIdForUpdate(id).orElseThrow(AppointmentNotFoundException::new);
    }

    /** Books a registered (CURRENT) patient as Confirmed. */
    @Transactional
    public Appointment book(Patient patient, Branch branch, Staff staff, Service service, Room room,
                             LocalDate appointmentDate, LocalTime appointmentTime, Long actorId) {
        return book(patient, branch, staff, service, room, appointmentDate, appointmentTime,
                AppointmentStatus.Confirmed, actorId);
    }

    /** Books a registered (CURRENT) patient; a new booking starts Unconfirmed or Confirmed. */
    @Transactional
    public Appointment book(Patient patient, Branch branch, Staff staff, Service service, Room room,
                             LocalDate appointmentDate, LocalTime appointmentTime, AppointmentStatus initialStatus,
                             Long actorId) {
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
        appointment.setAppointmentPatientType(AppointmentPatientType.CURRENT);
        appointment.setAppointmentStatus(requireInitialStatus(initialStatus));
        appointment.setAppointmentCreateAt(Instant.now());
        appointment.setAppointmentCreateBy(actorId);
        return appointments.save(appointment);
    }

    /**
     * Books a patient who is not registered yet. They are identified by a name
     * (first and last) or by a nickname plus phone number; a single-word name is
     * treated as a nickname, so it needs the phone.
     */
    @Transactional
    public Appointment bookNewPatient(String contactName, String contactPhone, Branch branch, Staff staff,
                                       RequestedServiceType requestedServiceType, Room room,
                                       LocalDate appointmentDate, LocalTime appointmentTime,
                                       AppointmentStatus initialStatus, Long actorId) {
        String name = contactName == null ? "" : contactName.trim();
        String phone = contactPhone == null || contactPhone.isBlank() ? null : contactPhone.trim();
        require(!name.isEmpty(), "ต้องระบุชื่อ-นามสกุล หรือชื่อเล่นของผู้ป่วยใหม่");
        require(phone != null, "ต้องระบุเบอร์โทรผู้ป่วยใหม่");
        require(name.contains(" ") || phone != null, "หากระบุเพียงชื่อเล่นต้องระบุเบอร์โทรด้วย");
        require(branch != null, "ต้องระบุสาขา");
        require(staff != null, "ต้องระบุผู้ให้บริการ");
        require(appointmentDate != null, "ต้องระบุวันที่นัด");
        require(appointmentTime != null, "ต้องระบุเวลานัด");
        requireNoConflict(staff.getId(), appointmentDate, appointmentTime, null);

        Appointment appointment = new Appointment();
        appointment.setPatient(null);
        appointment.setAppointmentPatientType(AppointmentPatientType.NEW);
        appointment.setContactName(name);
        appointment.setContactPhone(phone);
        appointment.setRequestedServiceType(requestedServiceType);
        appointment.setBranch(branch);
        appointment.setStaff(staff);
        appointment.setRoom(room);
        appointment.setAppointmentDate(appointmentDate);
        appointment.setAppointmentTime(appointmentTime);
        appointment.setAppointmentStatus(requireInitialStatus(initialStatus));
        appointment.setAppointmentCreateAt(Instant.now());
        appointment.setAppointmentCreateBy(actorId);
        return appointments.save(appointment);
    }

    @Transactional
    public Appointment confirm(Long id, Long actorId) {
        Appointment appointment = find(id);
        require(appointment.getAppointmentStatus() == AppointmentStatus.Unconfirmed, "นัดหมายนี้ไม่ได้อยู่ในสถานะยังไม่ยืนยัน");
        changeStatus(appointment, AppointmentStatus.Confirmed, "ยืนยันนัดหมาย", actorId);
        appointment.setAppointmentUpdateAt(Instant.now());
        appointment.setAppointmentUpdateBy(actorId);
        return appointments.save(appointment);
    }

    /** After a NEW patient registers: the appointment becomes a CURRENT one for that patient. */
    @Transactional
    public Appointment linkRegisteredPatient(Long id, Patient patient, Long actorId) {
        Appointment appointment = getForUpdate(id);
        requireNotTerminal(appointment);
        require(patient != null, "ต้องระบุผู้ป่วย");
        require(appointment.getAppointmentPatientType() == AppointmentPatientType.NEW && appointment.getPatient() == null,
                "นัดหมายนี้ผูกกับผู้ป่วยที่ลงทะเบียนแล้ว");
        appointment.setPatient(patient);
        appointment.setAppointmentPatientType(AppointmentPatientType.CURRENT);
        appointment.setAppointmentUpdateAt(Instant.now());
        appointment.setAppointmentUpdateBy(actorId);
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
        Appointment appointment = find(id);
        require(appointment.getAppointmentStatus() != AppointmentStatus.Unconfirmed, "ต้องยืนยันนัดหมายก่อนเช็คอิน");
        require(appointment.getPatient() != null, "ต้องลงทะเบียนผู้ป่วยก่อนเช็คอิน");
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

    private static AppointmentStatus requireInitialStatus(AppointmentStatus status) {
        AppointmentStatus initial = status == null ? AppointmentStatus.Confirmed : status;
        require(initial == AppointmentStatus.Unconfirmed || initial == AppointmentStatus.Confirmed,
                "นัดหมายใหม่ต้องเริ่มที่สถานะยังไม่ยืนยันหรือยืนยันแล้วเท่านั้น");
        return initial;
    }

    private void requireNoConflict(Long staffId, LocalDate date, LocalTime time, Long excludingAppointmentId) {
        boolean conflict = excludingAppointmentId == null
                ? appointments.existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn(
                    staffId, date, time, AppointmentStatus.slotReleasing())
                : appointments.existsByStaffIdAndAppointmentDateAndAppointmentTimeAndIdNotAndAppointmentStatusNotIn(
                    staffId, date, time, excludingAppointmentId, AppointmentStatus.slotReleasing());
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
