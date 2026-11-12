package com.sabyeguyz.clinicv2.calendar;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints to look into the fake Google Calendar and to edit it like a person would. */
@Profile("cypress-v2")
@RestController
@RequestMapping("/__cypress/calendar")
@PreAuthorize("hasRole('MANAGER')")
public class CypressCalendarFakeController {
    public record EditRequest(String calendarId, Long appointmentId, String start, boolean cancel) {}

    public record BusyRequest(String calendarId, String start, String end) {}

    private final FakeGoogleCalendarClient fake;

    public CypressCalendarFakeController(FakeGoogleCalendarClient fake) {
        this.fake = fake;
    }

    @GetMapping("/events")
    public List<Map<String, Object>> events(@RequestParam String calendarId) {
        return fake.events(calendarId).stream().map(e -> Map.<String, Object>of(
                "id", e.id, "appointmentId", e.appointmentId, "summary", e.summary, "description", e.description,
                "location", e.location == null ? "" : e.location, "start", e.start.toString(), "end", e.end.toString(),
                "cancelled", e.cancelled)).toList();
    }

    @PostMapping("/edit")
    public Map<String, Object> edit(@RequestBody EditRequest request) {
        FakeGoogleCalendarClient.FakeEvent event = fake.edit(request.calendarId(), request.appointmentId(),
                request.start() == null ? null : LocalDateTime.parse(request.start()), request.cancel());
        return Map.of("id", event.id, "start", event.start.toString(), "cancelled", event.cancelled);
    }

    /** A person adds a Busy event by hand in the calendar. */
    @PostMapping("/busy")
    public Map<String, Object> busy(@RequestBody BusyRequest request) {
        fake.addBusy(request.calendarId(), LocalDateTime.parse(request.start()), LocalDateTime.parse(request.end()));
        return Map.of("added", true);
    }

    @PostMapping("/fail-busy")
    public Map<String, Object> failBusy(@RequestParam int count) {
        fake.failBusyNext(count);
        return Map.of("failing", count);
    }

    @PostMapping("/fail")
    public Map<String, Object> fail(@RequestParam int count) {
        fake.failNext(count);
        return Map.of("failing", count);
    }
}
