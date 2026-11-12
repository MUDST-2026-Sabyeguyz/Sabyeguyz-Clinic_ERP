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
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class CalendarController {
    public record BranchStatus(String branchCode, String branchName, String googleCalendarId, long waiting, long failed) {}

    /** keySource: "ui" (key saved in the web UI), "file" (key file on the server), "env" (OAuth values) or "none". */
    public record Status(boolean enabled, int defaultDurationMinutes, Instant lastRunAt,
                         CalendarSyncService.Result lastResult, List<BranchStatus> branches,
                         String keySource, String serviceAccountEmail) {}

    public record CredentialsRequest(String keyJson) {}

    public record CalendarRequest(String googleCalendarId) {}

    private final CalendarSyncService sync;
    private final CalendarProperties properties;
    private final CalendarCredentialStore credentials;
    private final org.springframework.beans.factory.ObjectProvider<GoogleCalendarClient> client;
    private final com.sabyeguyz.clinicv2.auth.CurrentActor actor;
    private final BranchRepository branches;
    private final AppointmentCalendarRepository appointments;
    private final com.sabyeguyz.clinicv2.auth.BranchAccessPolicy branchAccess;

    public CalendarController(CalendarSyncService sync, CalendarProperties properties, BranchRepository branches,
                              AppointmentCalendarRepository appointments,
                              com.sabyeguyz.clinicv2.auth.BranchAccessPolicy branchAccess,
                              CalendarCredentialStore credentials,
                              org.springframework.beans.factory.ObjectProvider<GoogleCalendarClient> client,
                              com.sabyeguyz.clinicv2.auth.CurrentActor actor) {
        this.credentials = credentials;
        this.client = client;
        this.actor = actor;
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
        boolean manager = own == null;
        return new Status(sync.isEnabled(), properties.getDefaultDurationMinutes(), sync.lastRunAt(),
                manager ? sync.lastResult() : null, rows, keySource(),
                manager ? credentials.clientEmail().orElse(null) : null);
    }

    private String keySource() {
        if (!properties.getServiceAccountKeyFile().isBlank()) return "file";
        if (credentials.isConfigured()) return "ui";
        if (properties.isEnabled() && !properties.getRefreshToken().isBlank()) return "env";
        return "none";
    }

    /**
     * Saves the Google service account key (the JSON file's text) pasted by a Manager. The key is checked first: it must
     * be a service account key and Google must accept it. It is stored encrypted and never sent back.
     */
    @PutMapping("/credentials")
    @PreAuthorize("hasRole('MANAGER')")
    public Status saveCredentials(@RequestBody CredentialsRequest request) {
        String keyJson = request == null || request.keyJson() == null ? "" : request.keyJson().trim();
        if (keyJson.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "กรุณาวางเนื้อหาไฟล์ key (JSON) หรือเลือกไฟล์");
        }
        if (keyJson.length() > 20_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ไฟล์ใหญ่เกินไป ไม่ใช่ไฟล์ key ของ Google");
        }
        ServiceAccountCredentials parsed;
        try {
            parsed = credentials.parse(keyJson);
        } catch (CalendarApiException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ไฟล์ key ไม่ถูกต้อง ต้องเป็นไฟล์ JSON ของ service account ที่มี client_email และ private_key");
        }
        GoogleCalendarClient google = client.getIfAvailable();
        if (google != null) {
            try {
                google.verifyCredentials(parsed);
            } catch (CalendarApiException refused) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, refused.getStatus() == 0
                        ? "เชื่อมต่อ Google ไม่ได้ในขณะนี้ กรุณาลองใหม่"
                        : "Google ไม่ยอมรับ key นี้ ตรวจว่า key ยังไม่ถูกลบและ Google Calendar API เปิดใช้อยู่");
            }
        }
        credentials.save(parsed, keyJson, actor.currentId());
        return status();
    }

    /** Removes the key saved in the UI (a key file configured on the server is not affected). */
    @DeleteMapping("/credentials")
    @PreAuthorize("hasRole('MANAGER')")
    public Status removeCredentials() {
        credentials.clear();
        return status();
    }

    /** Sets (or, with an empty value, clears) the calendar of one branch; its appointments are then synced. */
    @PutMapping("/branches/{branchCode}")
    @PreAuthorize("hasRole('MANAGER')")
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
    @PreAuthorize("hasRole('MANAGER')")
    public CalendarSyncService.Result syncNow() {
        if (!sync.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "การซิงก์ Google Calendar ยังไม่ได้เปิดใช้งานหรือยังตั้งค่าไม่ครบ");
        }
        return sync.runOnce();
    }
}
