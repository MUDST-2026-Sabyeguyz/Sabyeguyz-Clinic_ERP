package com.sabyeguyz.clinicv2.appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.AppointmentPatientType;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.enums.RequestedServiceType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.resource.Room;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffRepository;

/** Appointment booking (Clinic ERP Workflow Brief §4). */
@org.springframework.stereotype.Service
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final AppointmentStatusHistoryRepository history;
    private final StaffRepository staffRepository;

    public AppointmentService(
            AppointmentRepository appointments,
            AppointmentStatusHistoryRepository history,
            StaffRepository staffRepository) {
        this.appointments = appointments;
        this.history = history;
        this.staffRepository = staffRepository;
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

    /** Books a registered (CURRENT) patient as Unconfirmed. */
    @Transactional
    public Appointment book(
            Patient patient,
            Branch branch,
            Staff staff,
            Service service,
            Room room,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            Long actorId) {
        return book(
                patient,
                branch,
                staff,
                service,
                room,
                appointmentDate,
                appointmentTime,
                AppointmentStatus.Unconfirmed,
                actorId);
    }

    @Transactional
    public Appointment book(
            Patient patient,
            Branch branch,
            Staff staff,
            Service service,
            Room room,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            AppointmentStatus initialStatus,
            Long actorId) {
        return book(
                patient,
                branch,
                staff,
                service,
                room,
                appointmentDate,
                appointmentTime,
                endAt(appointmentDate, appointmentTime, service, branch),
                initialStatus,
                actorId);
    }

    /** Books a registered (CURRENT) patient; a new booking starts Unconfirmed or Confirmed. */
    @Transactional
    public Appointment book(
            Patient patient,
            Branch branch,
            Staff staff,
            Service service,
            Room room,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            java.time.LocalDateTime appointmentEndAt,
            AppointmentStatus initialStatus,
            Long actorId) {
        require(patient != null, "ต้องระบุผู้ป่วย");
        require(branch != null, "ต้องระบุสาขา");
        require(staff != null, "ต้องระบุผู้ให้บริการ");
        require(appointmentDate != null, "ต้องระบุวันที่นัด");
        require(appointmentTime != null, "ต้องระบุเวลานัด");

        java.time.LocalDateTime bookingEnd = requireEndAt(
                appointmentDate,
                appointmentTime,
                appointmentEndAt);

        requireFuture(
                appointmentDate,
                appointmentTime);
        requireNoConflict(
                staff.getId(),
                appointmentDate,
                appointmentTime,
                null);
        requireSlotFits(
                branch,
                staff,
                appointmentDate,
                appointmentTime,
                bookingEnd,
                null);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setBranch(branch);
        appointment.setStaff(staff);
        appointment.setService(service);
        appointment.setRoom(room);
        appointment.setAppointmentDate(appointmentDate);
        appointment.setAppointmentTime(appointmentTime);
        appointment.setAppointmentEndAt(bookingEnd);
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
    public Appointment bookNewPatient(
            String contactName,
            String contactPhone,
            Branch branch,
            Staff staff,
            RequestedServiceType requestedServiceType,
            Room room,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            AppointmentStatus initialStatus,
            Long actorId) {
        return bookNewPatient(
                contactName,
                contactPhone,
                branch,
                staff,
                requestedServiceType,
                null,
                room,
                appointmentDate,
                appointmentTime,
                initialStatus,
                actorId);
    }

    @Transactional
    public Appointment bookNewPatient(
            String contactName,
            String contactPhone,
            Branch branch,
            Staff staff,
            RequestedServiceType requestedServiceType,
            Service service,
            Room room,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            AppointmentStatus initialStatus,
            Long actorId) {
        return bookNewPatient(
                contactName,
                contactPhone,
                branch,
                staff,
                requestedServiceType,
                service,
                room,
                appointmentDate,
                appointmentTime,
                endAt(appointmentDate, appointmentTime, service, branch),
                initialStatus,
                actorId);
    }

    @Transactional
    public Appointment bookNewPatient(
            String contactName,
            String contactPhone,
            Branch branch,
            Staff staff,
            RequestedServiceType requestedServiceType,
            Service service,
            Room room,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            java.time.LocalDateTime appointmentEndAt,
            AppointmentStatus initialStatus,
            Long actorId) {
        String name = contactName == null ? "" : contactName.trim();
        String phone = contactPhone == null || contactPhone.isBlank()
                ? null
                : contactPhone.trim();

        require(!name.isEmpty(), "ต้องระบุชื่อ-นามสกุล หรือชื่อเล่นของผู้ป่วยใหม่");
        require(phone != null, "ต้องระบุเบอร์โทรผู้ป่วยใหม่");
        require(phone.matches("[0-9]{1,10}"), "เบอร์โทรต้องเป็นตัวเลขไม่เกิน 10 หลัก");
        require(name.contains(" ") || phone != null, "หากระบุเพียงชื่อเล่นต้องระบุเบอร์โทรด้วย");
        require(branch != null, "ต้องระบุสาขา");
        require(staff != null, "ต้องระบุผู้ให้บริการ");
        require(appointmentDate != null, "ต้องระบุวันที่นัด");
        require(appointmentTime != null, "ต้องระบุเวลานัด");

        java.time.LocalDateTime bookingEnd = requireEndAt(
                appointmentDate,
                appointmentTime,
                appointmentEndAt);
        requireFuture(
                appointmentDate,
                appointmentTime);
        requireNoConflict(
                staff.getId(),
                appointmentDate,
                appointmentTime,
                null);
        requireSlotFits(
                branch,
                staff,
                appointmentDate,
                appointmentTime,
                bookingEnd,
                null);

        Appointment appointment = new Appointment();
        appointment.setPatient(null);
        appointment.setAppointmentPatientType(AppointmentPatientType.NEW);
        appointment.setContactName(name);
        appointment.setContactPhone(phone);
        appointment.setRequestedServiceType(requestedServiceType);
        appointment.setBranch(branch);
        appointment.setStaff(staff);
        appointment.setService(service);
        appointment.setRoom(room);
        appointment.setAppointmentDate(appointmentDate);
        appointment.setAppointmentTime(appointmentTime);
        appointment.setAppointmentEndAt(bookingEnd);
        appointment.setAppointmentStatus(requireInitialStatus(initialStatus));
        appointment.setAppointmentCreateAt(Instant.now());
        appointment.setAppointmentCreateBy(actorId);

        return appointments.save(appointment);
    }

    @Transactional
    public Appointment requireServiceAppointment(
            Long id,
            Patient patient,
            Branch branch) {
        Appointment appointment = getForUpdate(id);

        if (appointment.getAppointmentStatus()
                != AppointmentStatus.Unconfirmed) {
            throw new IllegalStateException("นัดนี้ไม่ได้อยู่ในสถานะรอรับบริการ");
        }

        require(patient != null && patient.getId() != null,
                "ต้องระบุผู้ป่วยที่ลงทะเบียนแล้ว");
        require(branch != null
                        && branch.getId() != null
                        && appointment.getBranch() != null
                        && branch.getId().equals(
                        appointment.getBranch().getId()),
                "สาขารับบริการต้องตรงกับสาขานัดหมาย");

        if (appointment.getAppointmentPatientType()
                == AppointmentPatientType.CURRENT) {
            require(appointment.getPatient() != null
                            && patient.getId().equals(appointment.getPatient().getId()),
                    "ผู้รับบริการต้องตรงกับผู้ป่วยในนัด CURRENT");
        } else {
            require(appointment.getPatient() == null, "นัด NEW ต้องไม่ผูกผู้ป่วยย้อนหลัง");
        }
        return appointment;
    }

    @Transactional
    public Appointment confirm(Long id, Long actorId) {
        throw new IllegalStateException("ต้องบันทึกบริการและรับชำระ หรือใช้สิทธิ์คอร์สสำเร็จก่อนยืนยันนัด");
    }

    @Transactional
    public Appointment confirmAfterService(
            Long id,
            Long actorId) {
        Appointment appointment = getForUpdate(id);

        require(
                appointment.getAppointmentStatus() == AppointmentStatus.Unconfirmed,
                "นัดหมายนี้ไม่ได้อยู่ในสถานะยังไม่ยืนยัน");

        changeStatus(
                appointment, AppointmentStatus.Confirmed,
                "ยืนยันหลังรับบริการสำเร็จ", actorId);

        appointment.setAppointmentUpdateAt(Instant.now());
        appointment.setAppointmentUpdateBy(actorId);

        return appointments.save(appointment);
    }

    /** NEW appointments retain their original registration state. */
    @Transactional
    public Appointment linkRegisteredPatient(
            Long id,
            Patient patient,
            Long actorId) {
        throw new IllegalStateException(
                "นัด NEW ไม่ผูกผู้ป่วยย้อนหลัง " + "ให้ส่ง patientId จากการลงทะเบียนไปหน้าบริการ");
    }

    /** Moves the same appointment to a new date/time; does not create a new row. */
    @Transactional
    public Appointment reschedule(
            Long id,
            LocalDate newDate,
            LocalTime newTime,
            String reason,
            Long actorId) {
        Appointment appointment = getForUpdate(id);
        return reschedule(
                appointment,
                appointment.getBranch(),
                appointment.getStaff(),
                appointment.getService(),
                newDate,
                newTime,
                reason,
                actorId);
    }

    /** Updates the booking details while retaining the appointment record and history. */
    @Transactional
    public Appointment reschedule(
            Long id,
            Branch branch,
            Staff staff,
            Service service,
            LocalDate newDate,
            LocalTime newTime,
            String reason,
            Long actorId) {
        return reschedule(
                getForUpdate(id),
                branch,
                staff,
                service,
                newDate,
                newTime,
                reason,
                actorId);
    }

    @Transactional
    public Appointment reschedule(
            Long id,
            Branch branch,
            Staff staff,
            Service service,
            LocalDate newDate,
            LocalTime newTime,
            java.time.LocalDateTime appointmentEndAt,
            String reason,
            Long actorId) {
        return reschedule(
                getForUpdate(id),
                branch,
                staff,
                service,
                newDate,
                newTime,
                appointmentEndAt,
                reason,
                actorId);
    }

    private Appointment reschedule(
            Appointment appointment,
            Branch branch,
            Staff staff,
            Service service,
            LocalDate newDate,
            LocalTime newTime,
            String reason,
            Long actorId) {
        java.time.LocalDateTime originalStart = appointment.getAppointmentDate()
                .atTime(appointment.getAppointmentTime());
        java.time.LocalDateTime originalEnd = requireEndAt(
                appointment.getAppointmentDate(),
                appointment.getAppointmentTime(),
                appointment.getAppointmentEndAt());
        java.time.LocalDateTime movedEnd = newDate.atTime(newTime)
                .plus(java.time.Duration.between(originalStart, originalEnd));

        return reschedule(
                appointment,
                branch,
                staff,
                service,
                newDate,
                newTime,
                movedEnd,
                reason,
                actorId);
    }

    private Appointment reschedule(
            Appointment appointment,
            Branch branch,
            Staff staff,
            Service service,
            LocalDate newDate,
            LocalTime newTime,
            java.time.LocalDateTime appointmentEndAt,
            String reason,
            Long actorId) {
        requireNotTerminal(appointment);
        require(branch != null, "ต้องระบุสาขา");
        require(staff != null, "ต้องระบุนักกายภาพ");
        require(branch.getId().equals(staff.getBranch().getId()), "นักกายภาพต้องอยู่สาขาเดียวกับนัดหมาย");
        require(newDate != null, "ต้องระบุวันที่นัดใหม่");
        require(newTime != null, "ต้องระบุเวลานัดใหม่");

        java.time.LocalDateTime bookingEnd = requireEndAt(newDate, newTime, appointmentEndAt);
        if (!newDate.equals(appointment.getAppointmentDate()) || !newTime.equals(appointment.getAppointmentTime())) {
            requireFuture(newDate, newTime);
        }
        requireNoConflict(staff.getId(), newDate, newTime, appointment.getId());
        requireSlotFits(branch, staff, newDate, newTime, bookingEnd, appointment.getId());

        appointment.setBranch(branch);
        appointment.setStaff(staff);
        appointment.setService(service);
        appointment.setAppointmentDate(newDate);
        appointment.setAppointmentTime(newTime);
        appointment.setAppointmentEndAt(bookingEnd);
        appointment.setAppointmentUpdateAt(Instant.now());
        appointment.setAppointmentUpdateBy(actorId);

        return appointments.save(appointment);
    }

    @Transactional
    public Appointment cancel(
            Long id,
            String reason,
            Long actorId) {
        Appointment appointment = find(id);
        requireNotTerminal(appointment);
        require(reason != null && !reason.isBlank(), "ต้องระบุเหตุผลที่ยกเลิก");
        changeStatus(appointment,
                AppointmentStatus.Cancelled,
                reason,
                actorId);
        appointment.setAppointmentUpdateAt(Instant.now());
        appointment.setAppointmentUpdateBy(actorId);

        return appointments.save(appointment);
    }

    @Transactional
    public Appointment markNoShow(
            Long id,
            Long actorId) {
        throw new IllegalStateException("ไม่รองรับสถานะ NoShow ให้ยกเลิกนัดพร้อมระบุเหตุผล");
    }

    @Transactional
    public Appointment markArrived(
            Long id,
            Long actorId) {
        throw new IllegalStateException("ไม่รองรับสถานะ Arrived ของนัด ให้ดำเนินการผ่านหน้าบริการ");
    }

    @Transactional
    public Appointment markInService(
            Long id,
            Long actorId) {
        throw new IllegalStateException("ไม่รองรับสถานะ InService ของนัด ให้ดำเนินการผ่านหน้าบริการ");
    }

    @Transactional
    public Appointment markCompleted(
            Long id,
            Long actorId) {
        throw new IllegalStateException("นัดจะยืนยันเมื่อบันทึกบริการและรับชำระ หรือใช้สิทธิ์คอร์สสำเร็จ");
    }

    /** Applies the new status and writes an Appointment_status_history row (audit trail). */
    private void changeStatus(
            Appointment appointment,
            AppointmentStatus newStatus,
            String reason,
            Long actorId) {
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
        AppointmentStatus initial = status == null
                ? AppointmentStatus.Unconfirmed
                : status;
        require(initial == AppointmentStatus.Unconfirmed,
                "นัดหมายใหม่ต้องเริ่มที่สถานะยังไม่ยืนยันเท่านั้น");

        return initial;
    }

    private void requireNoConflict(
            Long staffId,
            LocalDate date,
            LocalTime time,
            Long excludingAppointmentId) {
        boolean conflict = excludingAppointmentId == null
                ? appointments.existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn(
                        staffId, date, time, AppointmentStatus.slotReleasing())
                : appointments.existsByStaffIdAndAppointmentDateAndAppointmentTimeAndIdNotAndAppointmentStatusNotIn(
                        staffId, date, time, excludingAppointmentId, AppointmentStatus.slotReleasing());
        if (conflict) {
            throw new IllegalArgumentException("ผู้ให้บริการไม่ว่างในวันเวลาที่เลือก");
        }
    }

    /**
     * How long an appointment lasts: the service's own length, else the branch default, else unknown (null).
     * An unknown length keeps the old rule that only the exact same start time collides.
     */
    public static Integer effectiveMinutes(
            Service service,
            Branch branch) {
        if (service != null && service.getDefaultAppointmentMinutes() != null) {
            return service.getDefaultAppointmentMinutes();
        }
        return branch == null ? null : branch.getDefaultAppointmentMinutes();
    }

    private static java.time.LocalDateTime requireEndAt(
            LocalDate date,
            LocalTime time,
            java.time.LocalDateTime appointmentEndAt) {
        require(date != null, "ต้องระบุวันที่นัด");
        require(time != null, "ต้องระบุเวลานัด");
        require(appointmentEndAt != null, "ต้องระบุเวลาจบนัด");
        require(appointmentEndAt.isAfter(date.atTime(time)),
                "เวลาจบนัดต้องอยู่หลังเวลาเริ่มนัด");

        return appointmentEndAt;
    }

    private static java.time.LocalDateTime endAt(
            LocalDate date,
            LocalTime time,
            Service service,
            Branch branch) {
        Integer minutes = effectiveMinutes(service, branch);

        return minutes == null ? null : date.atTime(time).plusMinutes(minutes);
    }

    /** Two appointments overlap when each starts before the other ends (back to back is fine). */
    public static boolean overlaps(
            int startMinute,
            int length,
            int otherStartMinute,
            int otherLength) {
        return startMinute < otherStartMinute + otherLength
                && otherStartMinute < startMinute + length;
    }

    /** The slot must be inside the branch's booking window and must not overlap another appointment of the therapist. */
    private void requireSlotFits(
            Branch branch,
            Staff staff,
            Service service,
            LocalDate date,
            LocalTime time,
            Long excludingAppointmentId) {
        requireSlotFits(
                branch,
                staff,
                date,
                time,
                endAt(date, time, service, branch),
                excludingAppointmentId);
    }
    private void requireSlotFits(
            Branch branch,
            Staff staff,
            LocalDate date,
            LocalTime time,
            java.time.LocalDateTime appointmentEndAt,
            Long excludingAppointmentId) {
        java.time.LocalDateTime start = date.atTime(time);
        java.time.LocalDateTime end = requireEndAt(date, time, appointmentEndAt);

        require(branch != null, "ต้องระบุสาขา");
        require(staff != null && staff.getId() != null, "ต้องระบุนักกายภาพ");

        Staff lockedStaff = staffRepository.findByIdForUpdate(staff.getId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบนักกายภาพ"));

        require(lockedStaff.getBranch() != null
                        && branch.getId().equals(lockedStaff.getBranch().getId()),
                "นักกายภาพต้องอยู่สาขาเดียวกับนัดหมาย");

        LocalTime open = branch.getBookingOpenTime();
        LocalTime close = branch.getBookingCloseTime();
        require(open != null && close != null, "ต้องกำหนดช่วงเปิดรับนัดของสาขาก่อน");

        if (start.isBefore(date.atTime(open)) || end.isAfter(date.atTime(close))) {
            throw new IllegalArgumentException("เวลานัดอยู่นอกช่วงเปิดรับนัดของสาขา");
        }

        for (Appointment other : appointments.activeOfStaffForInterval(staff.getId(), start, end.toLocalDate(), AppointmentStatus.slotReleasing())) {
            if (other.getId() != null && other.getId().equals(excludingAppointmentId)) {
                continue;
            }

            java.time.LocalDateTime otherStart = other.getAppointmentDate().atTime(other.getAppointmentTime());
            java.time.LocalDateTime otherEnd = other.getAppointmentEndAt();

            if (otherStart.isBefore(end) && start.isBefore(otherEnd)) {
                throw new IllegalArgumentException("ผู้ให้บริการไม่ว่างในช่วงเวลาที่เลือก (ทับกับนัดอื่น)");
            }
        }
    }

    private static void requireNotTerminal(Appointment appointment) {
        AppointmentStatus status = appointment.getAppointmentStatus();

        if (status == AppointmentStatus.Completed
                || status == AppointmentStatus.Cancelled
                || status == AppointmentStatus.NoShow) {
            throw new IllegalArgumentException("นัดหมายนี้จบสถานะแล้ว ไม่สามารถแก้ไขได้อีก");
        }
    }

    private Appointment find(Long id) {
        return appointments.findById(id).orElseThrow(AppointmentNotFoundException::new);
    }

    private static void require(
            boolean condition,
            String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireFuture(
            LocalDate date,
            LocalTime time) {
        LocalDate today = ClinicTime.today();

        if (date.isBefore(today)
                || (date.equals(today)
                && !time.isAfter(ClinicTime.now().toLocalTime()))) {
            throw new IllegalArgumentException("ไม่สามารถจองเวลาย้อนหลังได้");
        }
    }
}
