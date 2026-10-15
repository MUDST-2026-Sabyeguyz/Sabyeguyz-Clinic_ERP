package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Free times a therapist can be booked on a day: inside the branch's booking window, not in the past and not
 * overlapping an appointment the therapist already holds. Only appointments of this system count: an event somebody
 * adds by hand in Google Calendar is not read back (the sync only follows the events it created itself).
 */
@org.springframework.stereotype.Service
public class AppointmentAvailabilityService {
    static final int STEP_MINUTES = 30;
    static final LocalTime FALLBACK_OPEN = LocalTime.of(9, 0);
    static final LocalTime FALLBACK_CLOSE = LocalTime.of(18, 0);

    public record Slot(String time, boolean available, String reason) {}

    public record Availability(LocalDate date, String openTime, String closeTime, boolean windowConfigured,
                               Integer durationMinutes, int stepMinutes, List<Slot> slots) {}

    private final AppointmentRepository appointments;

    public AppointmentAvailabilityService(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Transactional(readOnly = true)
    public Availability availability(Branch branch, Staff therapist, Service service, LocalDate date, Long excludingAppointmentId) {
        boolean configured = branch.getBookingOpenTime() != null && branch.getBookingCloseTime() != null;
        LocalTime open = configured ? branch.getBookingOpenTime() : FALLBACK_OPEN;
        LocalTime close = configured ? branch.getBookingCloseTime() : FALLBACK_CLOSE;
        Integer minutes = AppointmentService.effectiveMinutes(service, branch);
        int length = minutes == null ? STEP_MINUTES : minutes;

        List<Appointment> busy = appointments.activeOfStaffOnDate(therapist.getId(), date, AppointmentStatus.slotReleasing())
                .stream().filter(a -> excludingAppointmentId == null || !excludingAppointmentId.equals(a.getId())).toList();
        LocalDate today = ClinicTime.today();
        LocalTime now = ClinicTime.now().toLocalTime();

        List<Slot> slots = new ArrayList<>();
        for (LocalTime time = open; !time.plusMinutes(length).isAfter(close) && !time.plusMinutes(length).isBefore(time);
             time = time.plusMinutes(STEP_MINUTES)) {
            int start = time.getHour() * 60 + time.getMinute();
            String reason = null;
            if (date.isBefore(today) || (date.equals(today) && !time.isAfter(now))) {
                reason = "เลยเวลาแล้ว";
            } else {
                for (Appointment other : busy) {
                    Integer otherMinutes = AppointmentService.effectiveMinutes(other.getService(), other.getBranch());
                    LocalTime otherTime = other.getAppointmentTime();
                    int otherStart = otherTime.getHour() * 60 + otherTime.getMinute();
                    if (AppointmentService.overlaps(start, length, otherStart, otherMinutes == null ? STEP_MINUTES : otherMinutes)) {
                        reason = "มีนัดอื่นของนักกายภาพ";
                        break;
                    }
                }
            }
            slots.add(new Slot(time.toString().substring(0, 5), reason == null, reason));
            if (time.plusMinutes(STEP_MINUTES).isBefore(time)) break;
        }
        return new Availability(date, open.toString().substring(0, 5), close.toString().substring(0, 5), configured,
                minutes, STEP_MINUTES, slots);
    }
}
