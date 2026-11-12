package com.sabyeguyz.clinicv2.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sabyeguyz.clinicv2.appointment.Appointment;
import com.sabyeguyz.clinicv2.appointment.AppointmentService;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.calendar.GoogleCalendarClient.EventData;
import com.sabyeguyz.clinicv2.calendar.GoogleCalendarClient.EventRef;
import com.sabyeguyz.clinicv2.calendar.GoogleCalendarClient.RemoteEvent;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.enums.CalendarSyncStatus;

/**
 * Two-way sync between appointments and the branch Google Calendars (one calendar per branch).
 *
 * <p>System to Google: an appointment that changed after its last sync is written to its branch calendar
 * (created, moved, or removed when cancelled / no-show). A failed write leaves it dirty, so it is retried.</p>
 *
 * <p>Google to system: only two edits made in Google are accepted, for events this system created - moving the
 * time and cancelling - and only while the appointment is still Unconfirmed or Confirmed and the new time is
 * free. Anything else is put back in Google. The system stays the source of truth.</p>
 *
 * <p>Loops are avoided with the event etag: the etag Google returned for our own write is stored, so seeing the
 * same etag again means no new change. Event titles carry little personal data (HN, first name, service).</p>
 */
@Service
public class CalendarSyncService {
    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);
    private static final int BATCH = 100;

    public record Result(
            int pushed,
            int removed,
            int failed,
            int movedFromGoogle,
            int cancelledFromGoogle,
            int revertedInGoogle) {
        static Result empty() {
            return new Result(0, 0, 0, 0, 0, 0);
        }

        Result plus(Result other) {
            return new Result(
                    pushed + other.pushed,
                    removed + other.removed,
                    failed + other.failed,
                    movedFromGoogle + other.movedFromGoogle,
                    cancelledFromGoogle + other.cancelledFromGoogle,
                    revertedInGoogle + other.revertedInGoogle);
        }
    }

    private final CalendarProperties properties;
    private final CalendarCredentialStore credentials;
    private final ObjectProvider<GoogleCalendarClient> clientProvider;
    private final AppointmentCalendarRepository appointments;
    private final BranchRepository branches;
    private final AppointmentService appointmentService;
    private final TransactionTemplate tx;
    private final Map<String, Instant> lastPoll = new ConcurrentHashMap<>();
    private volatile Instant lastRunAt;
    private volatile Result lastResult = Result.empty();

    public CalendarSyncService(
            CalendarProperties properties,
            CalendarCredentialStore credentials,
            ObjectProvider<GoogleCalendarClient> clientProvider,
            AppointmentCalendarRepository appointments,
            BranchRepository branches,
            AppointmentService appointmentService,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.credentials = credentials;
        this.clientProvider = clientProvider;
        this.appointments = appointments;
        this.branches = branches;
        this.appointmentService = appointmentService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public boolean isEnabled() {
        return (properties.isEnabled()
                || credentials.isConfigured())
                && clientProvider.getIfAvailable() != null;
    }

    public Instant lastRunAt() {
        return lastRunAt;
    }

    public Result lastResult() {
        return lastResult;
    }

    /** One full round: write what changed here, then read what changed in Google. */
    public synchronized Result runOnce() {
        if (!isEnabled()) return Result.empty();

        Result result = push().plus(pull());
        if (result.revertedInGoogle() > 0) {
            result = result.plus(push()); // put the system's version back in Google now
        }

        lastRunAt = Instant.now();
        lastResult = result;

        return result;
    }

    // ---------------------------------------------------------------- system -> Google

    public synchronized Result push() {
        GoogleCalendarClient client = clientProvider.getIfAvailable();

        if (!isEnabled() || client == null) return Result.empty();

        LocalDate since = LocalDate.now(zone()).minusDays(1);
        List<Long> ids = tx.execute(
                status -> appointments.findDirtyIds(since, PageRequest.of(0, BATCH)));
        Result result = Result.empty();

        for (Long id : ids == null ? List.<Long>of() : ids) {
            Instant started = Instant.now();

            try {
                boolean removed = Boolean.TRUE.equals(
                        tx.execute(status -> pushOne(client, id, started)));

                result = result.plus(
                        removed
                                ? new Result(0, 1, 0, 0, 0, 0)
                                : new Result(1, 0, 0, 0, 0, 0));
            } catch (RuntimeException failure) {
                log.warn(
                        "Calendar push of appointment {} failed: {}",
                        id,
                        failure.getMessage());

                tx.executeWithoutResult(
                        status -> appointments.findById(id).ifPresent(
                                a -> a.setCalendarSyncStatus(CalendarSyncStatus.Failed)));

                result = result.plus(
                        new Result(0, 0, 1, 0, 0, 0));
            }
        }

        return result;
    }

    /** Returns true when the event was removed (or none was needed), false when it was written. */
    private boolean pushOne(
            GoogleCalendarClient client,
            Long id,
            Instant started) {
        Appointment appointment = appointments.findForSync(id).orElse(null);
        if (appointment == null) return true;

        String calendarId = appointment.getBranch().getGoogleCalendarId();
        if (calendarId == null || calendarId.isBlank()) return true;

        AppointmentStatus status = appointment.getAppointmentStatus();
        String eventId = appointment.getExternalCalendarEventId();

        if (status == AppointmentStatus.Cancelled || status == AppointmentStatus.NoShow) {
            if (eventId != null) client.delete(calendarId, eventId);

            appointment.setCalendarSyncStatus(CalendarSyncStatus.Deleted);
            appointment.setCalendarLastSyncedAt(started);

            return true;
        }

        EventData data = eventData(appointment);
        EventRef written;

        if (eventId == null) {
            written = client.insert(calendarId, data);
        } else {
            try {
                written = client.update(calendarId, eventId, data);
            } catch (CalendarApiException gone) {
                if (gone.getStatus() != 404 && gone.getStatus() != 410) throw gone;

                written = client.insert(calendarId, data);
            }
        }

        appointment.setExternalCalendarEventId(written.id());
        appointment.setCalendarEventEtag(written.etag());
        appointment.setCalendarSyncStatus(CalendarSyncStatus.Synced);
        appointment.setCalendarLastSyncedAt(started);

        return false;
    }

    private EventData eventData(Appointment appointment) {
        LocalDateTime start = LocalDateTime.of(
                appointment.getAppointmentDate(),
                appointment.getAppointmentTime());

        String who;
        if (appointment.getPatient() != null) {
            who = appointment.getPatient().getPatientHn() + " " + appointment.getPatient().getPatientFname();
        } else {
            String contact = appointment.getContactName() == null
                    ? ""
                    : appointment.getContactName().trim();
            who = "NEW " + (contact.contains(" ") ? contact.substring(0, contact.indexOf(' ')) : contact);
        }

        String service = appointment.getService() != null
                ? appointment.getService().getServiceName()
                : appointment.getRequestedServiceType() != null
                        ? appointment.getRequestedServiceType().name()
                        : "นัดหมาย";

        String prefix = appointment.getAppointmentStatus() == AppointmentStatus.Unconfirmed
                ? "[รอยืนยัน] "
                : "";

        String description = "นัดหมายเลขที่ " + appointment.getId() + "\n"
                + "ผู้ให้บริการ: " + appointment.getStaff().getStaffFname() + " " + appointment.getStaff().getStaffLname() + "\n"
                + "สถานะ: " + appointment.getAppointmentStatus().name() + "\n"
                + "(จัดการนัดหมายในระบบ Clinic ERP — การเลื่อนเวลาหรือยกเลิกใน Google จะสะท้อนกลับมาที่ระบบ)";

        return new EventData(
                appointment.getId(),
                (prefix + who + " - " + service).trim(),
                description,
                appointment.getBranch().getBranchName(),
                start,
                appointment.getAppointmentEndAt(),
                properties.getTimeZone());
    }

    // ---------------------------------------------------------------- Google -> system

    public synchronized Result pull() {
        GoogleCalendarClient client = clientProvider.getIfAvailable();

        if (!isEnabled() || client == null) return Result.empty();

        Result result = Result.empty();
        for (Branch branch : branches.findAll()) {
            String calendarId = branch.getGoogleCalendarId();

            if (calendarId == null || calendarId.isBlank()) continue;

            Instant pollStart = Instant.now();
            Instant since = lastPoll.getOrDefault(calendarId, pollStart.minusSeconds(properties.getLookbackHours() * 3600L));

            try {
                // The window is widened a little: seeing an event twice is harmless because of the etag check.
                for (RemoteEvent event : client.changedSince(
                        calendarId,
                        since.minusSeconds(60))) {
                    if (event.appointmentId() == null) continue;

                    result = result.plus(applyRemote(calendarId, event));
                }
                lastPoll.put(calendarId, pollStart);
            } catch (RuntimeException failure) {
                log.warn(
                        "Calendar poll of branch {} failed: {}",
                        branch.getBranchCode(),
                        failure.getMessage());
            }
        }

        return result;
    }

    private Result applyRemote(
            String calendarId,
            RemoteEvent event) {
        Long id = event.appointmentId();
        Appointment appointment = tx.execute(
                status -> appointments.findForSync(id).orElse(null));

        if (appointment == null
                || !calendarId.equals(appointment.getBranch().getGoogleCalendarId())) {
            return Result.empty();
        }
        if (event.etag() != null
                && event.etag().equals(appointment.getCalendarEventEtag())) {
            return Result.empty();
        }

        AppointmentStatus status = appointment.getAppointmentStatus();
        boolean open = status == AppointmentStatus.Unconfirmed
                || status == AppointmentStatus.Confirmed;
        long actor = properties.getActorUserId();

        if (event.cancelled()) {
            if (status == AppointmentStatus.Cancelled || status == AppointmentStatus.NoShow) {
                accept(id, event, null);

                return Result.empty();
            }
            if (open) {
                try {
                    tx.executeWithoutResult(s -> appointmentService.cancel(
                            id,
                            "ยกเลิกจาก Google Calendar",
                            actor));
                    accept(id, event, CalendarSyncStatus.Deleted);

                    return new Result(0, 0, 0, 0, 1, 0);
                } catch (RuntimeException refused) {
                    log.info("Calendar cancel of appointment {} refused: {}", id, refused.getMessage());
                }
            }

            // The visit already started or finished (or the cancel was refused): the event comes back.
            tx.executeWithoutResult(s -> appointments.findById(id).ifPresent(a -> {
                a.setExternalCalendarEventId(null);
                a.setCalendarEventEtag(null);
                a.setCalendarLastSyncedAt(null);
            }));

            return new Result(0, 0, 0, 0, 0, 1);
        }

        if (event.start() != null) {
            LocalDateTime current = LocalDateTime.of(
                    appointment.getAppointmentDate(),
                    appointment.getAppointmentTime());

            if (!event.start().equals(current)) {
                if (open) {
                    try {
                        tx.executeWithoutResult(s -> appointmentService.reschedule(
                                id,
                                event.start().toLocalDate(),
                                event.start().toLocalTime(),
                                "เลื่อนจาก Google Calendar",
                                actor));
                        accept(id, event, CalendarSyncStatus.Synced);

                        return new Result(0, 0, 0, 1, 0, 0);
                    } catch (RuntimeException refused) {
                        log.info(
                                "Calendar move of appointment {} refused: {}",
                                id,
                                refused.getMessage());
                    }
                }

                // Not allowed (visit started, slot taken, ...): write the system's time back to the event.
                tx.executeWithoutResult(s -> appointments.findById(id)
                        .ifPresent(a -> a.setCalendarLastSyncedAt(null)));
                return new Result(0, 0, 0, 0, 0, 1);
            }
        }

        accept(id, event, null);

        return Result.empty();
    }

    /** Remember the etag (and the sync time, so the change we just applied does not echo back). */
    private void accept(
            Long id,
            RemoteEvent event,
            CalendarSyncStatus status) {
        tx.executeWithoutResult(s -> appointments.findById(id).ifPresent(a -> {
            a.setCalendarEventEtag(event.etag());
            a.setCalendarLastSyncedAt(Instant.now());

            if (status != null) a.setCalendarSyncStatus(status);
        }));
    }

    private ZoneId zone() {
        return ZoneId.of(properties.getTimeZone());
    }
}
