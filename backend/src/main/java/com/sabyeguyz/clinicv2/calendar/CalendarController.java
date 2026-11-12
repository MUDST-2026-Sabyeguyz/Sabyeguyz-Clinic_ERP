package com.sabyeguyz.clinicv2.calendar;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Manager sees the state of the Google Calendar sync, sets each branch's calendar and runs a round now. Any signed-in
 * user can read the status of their own branch's calendar (to open it); nothing else is open to them.
 */
@RestController
@RequestMapping("/api/calendar")
@PreAuthorize("hasRole('MANAGER')")
public class CalendarController {
    public record BranchStatus(String branchCode, String branchName, String googleCalendarId, long waiting, long failed) {}

    public record Status(boolean enabled, int defaultDurationMinutes, Instant lastRunAt,
                         CalendarSyncService.Result lastResult, List<BranchStatus> branches) {}

    public record CalendarRequest(String googleCalendarId) {}

    private final CalendarSyncService sync;
    private final CalendarProperties properties;
    private final BranchRepository branches;
    private final AppointmentCalendarRepository appointments;
    private final com.sabyeguyz.clinicv2.auth.BranchAccessPolicy branchAccess;

    public CalendarController(CalendarSyncService sync, CalendarProperties properties, BranchRepository branches,
                              AppointmentCalendarRepository appointments,
                              com.sabyeguyz.clinicv2.auth.BranchAccessPolicy branchAccess) {
        this.branchAccess = branchAccess;
        this.sync = sync;
        this.properties = properties;
        this.branches = branches;
        this.appointments = appointments;
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Status status() {
        LocalDate since = LocalDate.now(ZoneId.of(properties.getTimeZone())).minusDays(1);
        // scope(null) is null only for a Manager (every branch); anyone else gets their own branch
        Branch own = branchAccess.scope(null);
        List<BranchStatus> rows = branches.findAll().stream()
                .filter(branch -> own == null || branch.getId().equals(own.getId()))
                .map(branch -> new BranchStatus(branch.getBranchCode(),
                branch.getBranchName(), branch.getGoogleCalendarId(),
                branch.getGoogleCalendarId() == null ? 0 : appointments.countDirty(branch.getId(), since),
                appointments.countFailed(branch.getId()))).toList();
        return new Status(sync.isEnabled(), properties.getDefaultDurationMinutes(), sync.lastRunAt(),
                own == null ? sync.lastResult() : null, rows);
    }

    /** Sets (or, with an empty value, clears) the calendar of one branch; its appointments are then synced. */
    @PutMapping("/branches/{branchCode}")
    @Transactional
    public BranchStatus setCalendar(@PathVariable String branchCode, @RequestBody CalendarRequest request) {
        Branch branch = branches.findByBranchCode(branchCode.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบสาขา " + branchCode));
        String calendarId = request.googleCalendarId() == null ? "" : request.googleCalendarId().trim();
        branch.setGoogleCalendarId(calendarId.isEmpty() ? null : calendarId);
        branches.save(branch);
        return new BranchStatus(branch.getBranchCode(), branch.getBranchName(), branch.getGoogleCalendarId(), 0, 0);
    }

    @PostMapping("/sync")
    public CalendarSyncService.Result syncNow() {
        if (!sync.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "การซิงก์ Google Calendar ยังไม่ได้เปิดใช้งานหรือยังตั้งค่าไม่ครบ");
        }
        return sync.runOnce();
    }
}
