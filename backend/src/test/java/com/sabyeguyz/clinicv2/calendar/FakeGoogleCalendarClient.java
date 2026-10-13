package com.sabyeguyz.clinicv2.calendar;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-memory stand-in for Google Calendar, used by the HTTP suite (profile cypress-v2) so no real Google account
 * is needed. The test endpoints in CypressCalendarFakeController read it and play the part of a person editing
 * the calendar in Google.
 */
@Primary
@Component
@Profile("cypress-v2")
public class FakeGoogleCalendarClient implements GoogleCalendarClient {
    public static class FakeEvent {
        public String id;
        public String etag;
        public Long appointmentId;
        public String summary;
        public String description;
        public String location;
        public LocalDateTime start;
        public LocalDateTime end;
        public boolean cancelled;
        public Instant updated;
    }

    private final Map<String, Map<String, FakeEvent>> calendars = new LinkedHashMap<>();
    private int sequence = 0;
    private int failures = 0;

    public synchronized void reset() {
        calendars.clear();
        sequence = 0;
        failures = 0;
    }

    /** The next n calls fail, like Google being unreachable. */
    public synchronized void failNext(int count) {
        this.failures = count;
    }

    public synchronized List<FakeEvent> events(String calendarId) {
        return new ArrayList<>(calendars.getOrDefault(calendarId, Map.of()).values());
    }

    /** A person moves the event in Google (start only) or deletes it. */
    public synchronized FakeEvent edit(String calendarId, Long appointmentId, LocalDateTime newStart, boolean cancel) {
        FakeEvent event = events(calendarId).stream().filter(e -> appointmentId.equals(e.appointmentId) && !e.cancelled)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("no such event"));
        if (newStart != null) {
            long minutes = Duration.between(event.start, event.end).toMinutes();
            event.start = newStart;
            event.end = newStart.plusMinutes(minutes);
        }
        if (cancel) event.cancelled = true;
        touch(event);
        return event;
    }

    private void touch(FakeEvent event) {
        event.etag = "etag-" + (++sequence);
        event.updated = Instant.now();
    }

    private void maybeFail() {
        if (failures > 0) {
            failures--;
            throw new CalendarApiException(503, "fake Google is down");
        }
    }

    @Override
    public synchronized EventRef insert(String calendarId, EventData data) {
        maybeFail();
        FakeEvent event = new FakeEvent();
        event.id = "evt-" + (++sequence);
        fill(event, data);
        touch(event);
        calendars.computeIfAbsent(calendarId, key -> new LinkedHashMap<>()).put(event.id, event);
        return new EventRef(event.id, event.etag);
    }

    @Override
    public synchronized EventRef update(String calendarId, String eventId, EventData data) {
        maybeFail();
        FakeEvent event = calendars.getOrDefault(calendarId, Map.of()).get(eventId);
        if (event == null || event.cancelled) throw new CalendarApiException(404, "no such event");
        fill(event, data);
        touch(event);
        return new EventRef(event.id, event.etag);
    }

    @Override
    public synchronized void delete(String calendarId, String eventId) {
        maybeFail();
        FakeEvent event = calendars.getOrDefault(calendarId, Map.of()).get(eventId);
        if (event != null) {
            event.cancelled = true;
            touch(event);
        }
    }

    @Override
    public synchronized List<RemoteEvent> changedSince(String calendarId, Instant updatedMin) {
        maybeFail();
        List<RemoteEvent> changed = new ArrayList<>();
        for (FakeEvent event : calendars.getOrDefault(calendarId, Map.of()).values()) {
            if (!event.updated.isBefore(updatedMin)) {
                changed.add(new RemoteEvent(event.id, event.etag, event.cancelled, event.start, event.appointmentId));
            }
        }
        return changed;
    }

    private static void fill(FakeEvent event, EventData data) {
        event.appointmentId = data.appointmentId();
        event.summary = data.summary();
        event.description = data.description();
        event.location = data.location();
        event.start = data.start();
        event.end = data.end();
    }
}
