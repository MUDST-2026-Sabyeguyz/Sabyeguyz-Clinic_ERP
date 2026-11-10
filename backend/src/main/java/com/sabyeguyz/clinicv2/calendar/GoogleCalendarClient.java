package com.sabyeguyz.clinicv2.calendar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** The few Google Calendar calls the sync needs; the real one talks HTTP, tests use an in-memory fake. */
public interface GoogleCalendarClient {
    /** What we write: the appointment id travels inside the event so a later change can be matched back. */
    record EventData(Long appointmentId, String summary, String description, String location,
                     LocalDateTime start, LocalDateTime end, String timeZone) {}

    record EventRef(String id, String etag) {}

    /** An event seen when asking for changes; start is in the clinic time zone, null for all-day events. */
    record RemoteEvent(String id, String etag, boolean cancelled, LocalDateTime start, Long appointmentId) {}

    /** A stretch of the calendar that is taken (Busy) by an event the clinic did not write; clinic-local times. */
    record BusyBlock(LocalDateTime start, LocalDateTime end) {}

    /** Checks that Google accepts a service account key (signs in once). Throws CalendarApiException when it does not. */
    default void verifyCredentials(ServiceAccountCredentials credentials) {}

    EventRef insert(String calendarId, EventData data);

    EventRef update(String calendarId, String eventId, EventData data);

    /** Deleting an event that is already gone is not an error. */
    void delete(String calendarId, String eventId);

    /** Events created or changed since the instant (cancelled ones included). */
    List<RemoteEvent> changedSince(String calendarId, Instant updatedMin);

    /**
     * The Busy time between the two clinic-local instants: events that are not cancelled, not shown as Free and not
     * written by this system (its own events are already appointments). An all-day event covers whole days.
     */
    List<BusyBlock> busyBlocks(String calendarId, LocalDateTime from, LocalDateTime to);
}
