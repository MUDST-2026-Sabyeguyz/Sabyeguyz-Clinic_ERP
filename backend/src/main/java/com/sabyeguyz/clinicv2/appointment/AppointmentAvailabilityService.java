package com.sabyeguyz.clinicv2.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.calendar.CalendarProperties;
import com.sabyeguyz.clinicv2.calendar.GoogleCalendarClient;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.staff.Staff;

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

    public record Slot(
            String time,
            boolean available,
            String reason) {}

    /** googleCalendar: NOT_CONNECTED (the branch has none), CHECKED (Busy read) or UNAVAILABLE (could not be read). */
    public record Availability(
            LocalDate date,
            String openTime,
            String closeTime,
            boolean windowConfigured,
            Integer durationMinutes,
            int stepMinutes,
            List<Slot> slots,
            String googleCalendar) {}

    private record GoogleBusy(
            String state,
            List<GoogleCalendarClient.BusyBlock> blocks) {}

    private final AppointmentRepository appointments;
    private final ObjectProvider<GoogleCalendarClient> googleClient;
    private final CalendarProperties calendarProperties;

    public AppointmentAvailabilityService(
            AppointmentRepository appointments,
            ObjectProvider<GoogleCalendarClient> googleClient,
            CalendarProperties calendarProperties) {
        this.appointments = appointments;
        this.googleClient = googleClient;
        this.calendarProperties = calendarProperties;
    }

    private GoogleBusy googleBusy(Branch branch, LocalDate date) {
        GoogleCalendarClient client = googleClient.getIfAvailable();
        String calendarId = branch.getGoogleCalendarId();

        if (!calendarProperties.isEnabled()
                || client == null
                || calendarId == null
                || calendarId.isBlank()) {
            return new GoogleBusy(
                    "NOT_CONNECTED",
                    List.of());
        }

        try {
            return new GoogleBusy(
                    "CHECKED",
                    client.busyBlocks(
                            calendarId,
                            date.atStartOfDay(),
                            date.plusDays(1).atStartOfDay()));
        } catch (RuntimeException unreachable) {
            return new GoogleBusy(
                    "UNAVAILABLE",
                    List.of()); // never block a booking because Google is down
        }
    }

    private static boolean touchesBusy(
            List<GoogleCalendarClient.BusyBlock> blocks,
            LocalDate date,
            int startMinute,
            int length) {
        for (GoogleCalendarClient.BusyBlock block : blocks) {
            int blockStart = (int) java.time.Duration
                    .between(date.atStartOfDay(), block.start())
                    .toMinutes();
            int blockEnd = (int) java.time.Duration
                    .between(date.atStartOfDay(), block.end())
                    .toMinutes();
            if (AppointmentService.overlaps(
                    startMinute,
                    length,
                    blockStart,
                    blockEnd - blockStart)) {
                return true;
            }
        }

        return false;
    }

    /** A booking is refused when it lands on Busy time in the branch's Google Calendar. */
    @Transactional(readOnly = true)
    public void requireNoGoogleBusy(
            Branch branch,
            java.time.LocalDateTime start,
            java.time.LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("ต้องระบุเวลาเริ่มและเวลาจบนัดให้ถูกต้อง");
        }

        for (LocalDate date = start.toLocalDate();
             !date.isAfter(end.toLocalDate());
             date = date.plusDays(1)) {
            GoogleBusy busy = googleBusy(branch, date);

            if ("UNAVAILABLE".equals(busy.state())) {
                throw new IllegalStateException("ตรวจเวลาว่างจาก Google Calendar ไม่สำเร็จ กรุณาลองใหม่");
            }
            for (GoogleCalendarClient.BusyBlock block : busy.blocks()) {
                if (start.isBefore(block.end())
                        && block.start().isBefore(end)) {
                    throw new IllegalArgumentException("เวลานัดตรงกับช่วงที่ติด Busy ใน Google Calendar ของสาขา");
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public Availability availability(
            Branch branch,
            Staff therapist,
            Service service,
            LocalDate date,
            Long excludingAppointmentId) {
        LocalTime open = branch.getBookingOpenTime();
        LocalTime close = branch.getBookingCloseTime();
        Integer minutes = AppointmentService.effectiveMinutes(service, branch);

        boolean configured = open != null && close != null && open.isBefore(close);

        if (!configured || minutes == null || minutes < 1) {
            return new Availability(
                    date,
                    open == null ? null : open.toString(),
                    close == null ? null : close.toString(),
                    configured,
                    minutes,
                    STEP_MINUTES,
                    List.of(),
                    "NOT_CHECKED");
        }

        List<Appointment> busy = appointments.activeOfStaffForInterval(
                therapist.getId(), date.atTime(open), date,
                AppointmentStatus.slotReleasing());

        java.time.LocalDateTime now = ClinicTime.now();
        GoogleBusy google = googleBusy(branch, date);
        List<Slot> slots = new ArrayList<>();

        for (java.time.LocalDateTime start = date.atTime(open);
             !start.plusMinutes(minutes).isAfter(date.atTime(close));
             start = start.plusMinutes(STEP_MINUTES)) {
            java.time.LocalDateTime end = start.plusMinutes(minutes);
            String reason = null;

            if (!start.isAfter(now)) {
                reason = "เลยเวลาแล้ว";
            } else if ("UNAVAILABLE".equals(google.state())) {
                reason = "ตรวจ Google Calendar ไม่สำเร็จ";
            } else {
                for (GoogleCalendarClient.BusyBlock block : google.blocks()) {
                    if (start.isBefore(block.end()) && block.start().isBefore(end)) {
                        reason = "ติด Busy ใน Google Calendar";
                        break;
                    }
                }
                if (reason == null) {
                    for (Appointment other : busy) {
                        if (other.getId() != null
                                && other.getId().equals(excludingAppointmentId)) {
                            continue;
                        }
                        java.time.LocalDateTime otherStart = other.getAppointmentDate().atTime(other.getAppointmentTime());
                        if (otherStart.isBefore(end)
                                && start.isBefore(other.getAppointmentEndAt())) {
                            reason = "มีนัดอื่นของนักกายภาพ";
                            break;
                        }
                    }
                }
            }
            slots.add(new Slot(start.toLocalTime().toString().substring(0, 5), reason == null, reason));
        }

        return new Availability(
                date,
                open.toString(),
                close.toString(),
                configured,
                minutes,
                STEP_MINUTES,
                slots,
                google.state());
    }
}
