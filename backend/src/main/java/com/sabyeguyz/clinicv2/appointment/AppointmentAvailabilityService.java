package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.calendar.CalendarProperties;
import com.sabyeguyz.clinicv2.calendar.GoogleCalendarClient;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

/**
 * Free times a therapist can be booked on a day: inside the branch's booking window, not in the past and not
 * overlapping an appointment the therapist already holds. When the branch has a Google Calendar, an event somebody added
 * by hand there that shows as Busy (a break, a closed hour, a holiday) takes the time away from the whole branch; the
 * clinic's own events are not counted twice. If Google cannot be reached the times are shown without it.
 */
@org.springframework.stereotype.Service
public class AppointmentAvailabilityService {
    static final int STEP_MINUTES = 30;
    static final LocalTime FALLBACK_OPEN = LocalTime.of(9, 0);
    static final LocalTime FALLBACK_CLOSE = LocalTime.of(18, 0);

    public record Slot(String time, boolean available, String reason) {}

    /** googleCalendar: NOT_CONNECTED (the branch has none), CHECKED (Busy read) or UNAVAILABLE (could not be read). */
    public record Availability(LocalDate date, String openTime, String closeTime, boolean windowConfigured,
                               Integer durationMinutes, int stepMinutes, List<Slot> slots, String googleCalendar) {}

    private record GoogleBusy(String state, List<GoogleCalendarClient.BusyBlock> blocks) {}

    private final AppointmentRepository appointments;
    private final ObjectProvider<GoogleCalendarClient> googleClient;
    private final CalendarProperties calendarProperties;

    public AppointmentAvailabilityService(AppointmentRepository appointments,
                                          ObjectProvider<GoogleCalendarClient> googleClient,
                                          CalendarProperties calendarProperties) {
        this.appointments = appointments;
        this.googleClient = googleClient;
        this.calendarProperties = calendarProperties;
    }

    private GoogleBusy googleBusy(Branch branch, LocalDate date) {
        GoogleCalendarClient client = googleClient.getIfAvailable();
        String calendarId = branch.getGoogleCalendarId();
        if (!calendarProperties.isEnabled() || client == null || calendarId == null || calendarId.isBlank()) {
            return new GoogleBusy("NOT_CONNECTED", List.of());
        }
        try {
            return new GoogleBusy("CHECKED", client.busyBlocks(calendarId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()));
        } catch (RuntimeException unreachable) {
            return new GoogleBusy("UNAVAILABLE", List.of()); // never block a booking because Google is down
        }
    }

    private static boolean touchesBusy(List<GoogleCalendarClient.BusyBlock> blocks, LocalDate date, int startMinute, int length) {
        for (GoogleCalendarClient.BusyBlock block : blocks) {
            int blockStart = (int) java.time.Duration.between(date.atStartOfDay(), block.start()).toMinutes();
            int blockEnd = (int) java.time.Duration.between(date.atStartOfDay(), block.end()).toMinutes();
            if (AppointmentService.overlaps(startMinute, length, blockStart, blockEnd - blockStart)) return true;
        }
        return false;
    }

    /** A booking is refused when it lands on Busy time in the branch's Google Calendar. */
    @Transactional(readOnly = true)
    public void requireNoGoogleBusy(Branch branch, com.sabyeguyz.clinicv2.catalog.Service service, LocalDate date, LocalTime time) {
        GoogleBusy busy = googleBusy(branch, date);
        if (busy.blocks().isEmpty()) return;
        Integer minutes = AppointmentService.effectiveMinutes(service, branch);
        if (touchesBusy(busy.blocks(), date, time.getHour() * 60 + time.getMinute(), minutes == null ? 0 : minutes)) {
            throw new IllegalArgumentException("เวลานัดตรงกับช่วงที่ติด Busy ใน Google Calendar ของสาขา");
        }
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
        GoogleBusy google = googleBusy(branch, date);

        List<Slot> slots = new ArrayList<>();
        for (LocalTime time = open; !time.plusMinutes(length).isAfter(close) && !time.plusMinutes(length).isBefore(time);
             time = time.plusMinutes(STEP_MINUTES)) {
            int start = time.getHour() * 60 + time.getMinute();
            String reason = null;
            if (date.isBefore(today) || (date.equals(today) && !time.isAfter(now))) {
                reason = "เลยเวลาแล้ว";
            } else if (touchesBusy(google.blocks(), date, start, length)) {
                reason = "ติด Busy ใน Google Calendar";
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
                minutes, STEP_MINUTES, slots, google.state());
    }
}
