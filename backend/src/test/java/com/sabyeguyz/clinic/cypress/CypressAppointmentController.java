package com.sabyeguyz.clinic.cypress;

import com.sabyeguyz.clinic.appointment.AppointmentRepository;
import com.sabyeguyz.clinic.appointment.AppointmentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Access to internal repository queries and fixture fields absent from the public API. */
@Profile("cypress")
@RestController
@RequestMapping("/__cypress")
@PreAuthorize("hasRole('MANAGER')")
public class CypressAppointmentController {
    private final AppointmentRepository appointments;
    private final JdbcTemplate jdbc;

    public CypressAppointmentController(AppointmentRepository appointments, JdbcTemplate jdbc) {
        this.appointments = appointments;
        this.jdbc = jdbc;
    }

    @GetMapping("/appointment-fixtures")
    public Map<String, Object> fixtures() {
        return Map.of("otherTherapistId", jdbc.queryForObject(
                "SELECT id FROM therapists WHERE code = 'RP-T02'", Long.class));
    }

    public record FixtureChange(boolean inactiveService, boolean inactiveTherapist, boolean otherBranch) {}

    @PostMapping("/appointment-fixtures")
    @Transactional
    public Map<String, Object> changeFixtures(@RequestBody FixtureChange change) {
        if (change.inactiveService()) jdbc.update("UPDATE services SET active = false WHERE code = 'PHYSIO_60'");
        if (change.inactiveTherapist()) jdbc.update("UPDATE therapists SET active = false WHERE code = 'RP-T02'");
        if (change.otherBranch()) {
            jdbc.update("INSERT INTO branches (code, numeric_code, name, active) VALUES ('QQ', '99', 'สาขาทดสอบ', true)");
            jdbc.update("INSERT INTO therapists (branch_id, code, full_name, active) VALUES ((SELECT id FROM branches WHERE code = 'QQ'), 'QQ-T01', 'นักกายภาพบำบัด สาขาอื่น', true)");
            return Map.of("otherBranchTherapistId", jdbc.queryForObject("SELECT id FROM therapists WHERE code = 'QQ-T01'", Long.class));
        }
        return Map.of();
    }

    public record OverlapQuery(Long therapistId, Instant startAt, Instant endAt, Long excludeAppointmentId) {}

    @PostMapping("/appointment-overlap")
    public boolean overlap(@RequestBody OverlapQuery query) {
        if (query.excludeAppointmentId() == null) {
            return appointments.existsOverlapping(query.therapistId(), query.startAt(), query.endAt(), AppointmentStatus.slotReleasing());
        }
        return appointments.existsOverlappingExcludingAppointment(query.therapistId(), query.startAt(), query.endAt(),
                AppointmentStatus.slotReleasing(), query.excludeAppointmentId());
    }

    @GetMapping("/appointment-slot-releasing")
    public Collection<AppointmentStatus> slotReleasing() {
        return AppointmentStatus.slotReleasing();
    }

    @GetMapping("/appointment-timestamp/{id}")
    public Map<String, Instant> timestamp(@PathVariable long id) {
        return Map.of("updatedAt", appointments.findById(id).orElseThrow().getUpdatedAt());
    }
}
