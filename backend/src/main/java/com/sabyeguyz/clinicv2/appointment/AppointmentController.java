package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.catalog.ServiceRepository;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's appointmentApi. Known gap, flagged
 * for the team: v2's Appointment has no end time/duration column (the
 * Service catalog dict has none either), so {@code endAt} in the response
 * always equals {@code startAt} - the frontend calendar/list views that
 * show an appointment's span will need a real fix once a duration source
 * exists, not just this API shape. Also: Appointment has no note/
 * cancel-reason columns (only Appointment_status_history.reason, written
 * internally by AppointmentService) - the response's note/cancelReason are
 * always null and CreateRequest.note is accepted but silently dropped.
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Bangkok");

    private final AppointmentService service;
    private final AppointmentRepository repository;
    private final PatientVisitService visitService;
    private final PatientVisitRepository visits;
    private final PatientRepository patients;
    private final BranchRepository branches;
    private final StaffRepository staff;
    private final ServiceRepository services;
    private final CurrentActor actor;

    public AppointmentController(AppointmentService service, AppointmentRepository repository,
                                  PatientVisitService visitService, PatientVisitRepository visits,
                                  PatientRepository patients, BranchRepository branches, StaffRepository staff,
                                  ServiceRepository services, CurrentActor actor) {
        this.service = service;
        this.repository = repository;
        this.visitService = visitService;
        this.visits = visits;
        this.patients = patients;
        this.branches = branches;
        this.staff = staff;
        this.services = services;
        this.actor = actor;
    }

    public record CreateRequest(String branchCode, Long patientId, Long therapistId, Long serviceId,
                                 OffsetDateTime startAt, String note) {}

    public record CancelRequest(String reason) {}

    public record RescheduleRequest(OffsetDateTime startAt, Long therapistId) {}

    public record PatientSummary(Long id, String hn, String fullName) {}
    public record TherapistSummary(Long id, String fullName) {}
    public record ServiceSummary(Long id, String name, int durationMinutes) {}

    public record Response(Long id, String branchCode, PatientSummary patient, TherapistSummary therapist,
                            ServiceSummary service, Instant startAt, Instant endAt, String status, String note,
                            String cancelReason) {}

    public record PageResponse(java.util.List<Response> items, int page, int size, long totalItems, int totalPages) {}

    @GetMapping
    public PageResponse search(@RequestParam(required = false) String branchCode,
                                @RequestParam(required = false) LocalDate date,
                                @RequestParam(required = false) Long therapistId,
                                @RequestParam(required = false) String status,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        Long branchId = branchCode == null || branchCode.isBlank() ? null : resolveBranch(branchCode).getId();
        AppointmentStatus statusValue = status == null || status.isBlank() ? null : AppointmentStatus.valueOf(status);
        Page<Appointment> result = repository.search(branchId, date, therapistId, statusValue,
                PageRequest.of(page, size, Sort.by("appointmentDate").ascending().and(Sort.by("appointmentTime").ascending())));
        return new PageResponse(result.getContent().stream().map(AppointmentController::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return toResponse(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody CreateRequest request) {
        Branch branch = resolveBranch(request.branchCode());
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        Staff therapist = staff.findById(request.therapistId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ให้บริการ"));
        Service svc = services.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
        OffsetDateTime local = request.startAt().atZoneSameInstant(CLINIC_ZONE).toOffsetDateTime();
        Appointment appointment = service.book(patient, branch, therapist, svc, null,
                local.toLocalDate(), local.toLocalTime(), actor.currentId());
        return toResponse(appointment);
    }

    @PostMapping("/{id}/check-in")
    public Response checkIn(@PathVariable Long id) {
        visitService.checkInFromAppointment(id, actor.currentId());
        return toResponse(service.get(id));
    }

    @PostMapping("/{id}/start-service")
    public Response startService(@PathVariable Long id) {
        PatientVisit visit = requireVisit(id);
        visitService.startService(visit.getId(), actor.currentId());
        return toResponse(service.get(id));
    }

    @PostMapping("/{id}/complete")
    public Response complete(@PathVariable Long id) {
        PatientVisit visit = requireVisit(id);
        visitService.complete(visit.getId(), actor.currentId());
        return toResponse(service.get(id));
    }

    @PostMapping("/{id}/no-show")
    public Response noShow(@PathVariable Long id) {
        return toResponse(service.markNoShow(id, actor.currentId()));
    }

    @PostMapping("/{id}/cancel")
    public Response cancel(@PathVariable Long id, @RequestBody CancelRequest request) {
        return toResponse(service.cancel(id, request.reason(), actor.currentId()));
    }

    @PatchMapping("/{id}")
    public Response reschedule(@PathVariable Long id, @RequestBody RescheduleRequest request) {
        OffsetDateTime local = request.startAt().atZoneSameInstant(CLINIC_ZONE).toOffsetDateTime();
        // Reassigning the therapist on reschedule is not supported yet (AppointmentService.reschedule
        // only moves date/time) - request.therapistId() is accepted but ignored, flagged for the team.
        return toResponse(service.reschedule(id, local.toLocalDate(), local.toLocalTime(),
                "เลื่อนนัดผ่าน API", actor.currentId()));
    }

    private PatientVisit requireVisit(Long appointmentId) {
        return visits.findFirstByAppointmentIdOrderByIdDesc(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("ยังไม่มีการเช็คอินสำหรับนัดหมายนี้"));
    }

    private Branch resolveBranch(String branchCode) {
        return branches.findByBranchCode(branchCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + branchCode));
    }

    private static Response toResponse(Appointment appointment) {
        Instant startAt = appointment.getAppointmentDate().atTime(appointment.getAppointmentTime())
                .atZone(CLINIC_ZONE).toInstant();
        return new Response(appointment.getId(), appointment.getBranch().getBranchCode(),
                new PatientSummary(appointment.getPatient().getId(), appointment.getPatient().getPatientHn(),
                        appointment.getPatient().getPatientFname() + " " + appointment.getPatient().getPatientLname()),
                new TherapistSummary(appointment.getStaff().getId(),
                        appointment.getStaff().getStaffFname() + " " + appointment.getStaff().getStaffLname()),
                new ServiceSummary(appointment.getService().getId(), appointment.getService().getServiceName(), 0),
                startAt, startAt, appointment.getAppointmentStatus().name(), null, null);
    }
}
