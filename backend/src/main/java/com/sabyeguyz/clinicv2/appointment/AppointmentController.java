package com.sabyeguyz.clinicv2.appointment;

import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.catalog.ServiceRepository;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.enums.RequestedServiceType;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's appointmentApi. End times are stored
 * when a service or branch duration is configured; older appointments without
 * an end time retain the previous response fallback. Notes use appointment.notes;
 * cancellation reasons are read from status history.
 */
@Transactional
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Bangkok");

    private final AppointmentService service;
    private final AppointmentRepository repository;
    private final PatientVisitService visitService;
    private final PatientVisitRepository visits;
    private final PatientRepository patients;
    private final StaffRepository staff;
    private final ServiceRepository services;
    private final CurrentActor actor;
    private final BranchAccessPolicy branchAccess;
    private final AppointmentStatusHistoryRepository history;
    private final AppointmentAvailabilityService availability;

    public AppointmentController(AppointmentService service, AppointmentRepository repository,
                                  PatientVisitService visitService, PatientVisitRepository visits,
                                  PatientRepository patients, StaffRepository staff,
                                  ServiceRepository services, CurrentActor actor, BranchAccessPolicy branchAccess,
                                  AppointmentStatusHistoryRepository history,
                                  AppointmentAvailabilityService availability) {
        this.service = service;
        this.repository = repository;
        this.visitService = visitService;
        this.visits = visits;
        this.patients = patients;
        this.staff = staff;
        this.services = services;
        this.actor = actor;
        this.branchAccess = branchAccess;
        this.history = history;
        this.availability = availability;
    }

    /**
     * patientType NEW books an unregistered patient by contactName (and
     * contactPhone); status may be Unconfirmed or Confirmed (default). The older
     * fields keep working unchanged for CURRENT patients.
     */
    public record CreateRequest(
            String branchCode, Long patientId, Long therapistId, Long serviceId,
            OffsetDateTime startAt, String note, String status, String patientType,
            String contactName, String contactPhone, String requestedServiceType,
            String contactMiddleName, OffsetDateTime endAt) {}

    public record LinkPatientRequest(Long patientId) {}

    public record CancelRequest(String reason) {}

    public record RescheduleRequest(
            OffsetDateTime startAt, Long therapistId, Long serviceId,
            String branchCode,
            com.fasterxml.jackson.databind.JsonNode note,
            OffsetDateTime endAt) {}

    public record PatientSummary(Long id, String hn, String fullName, String phone) {}
    public record TherapistSummary(Long id, String fullName) {}
    public record ServiceSummary(Long id, String name, int durationMinutes) {}

    public record Response(Long id, String branchCode, PatientSummary patient, TherapistSummary therapist,
                            ServiceSummary service, Instant startAt, Instant endAt, String status, String note,
                            String cancelReason, String patientType, String contactName, String contactPhone, String contactMiddleName) {}

    public record PageResponse(java.util.List<Response> items, int page, int size, long totalItems, int totalPages) {}

    @GetMapping
    public PageResponse search(@RequestParam(required = false) String branchCode,
                                @RequestParam(defaultValue = "") String query,
                                @RequestParam(required = false) LocalDate date,
                                @RequestParam(required = false) LocalDate startDate,
                                @RequestParam(required = false) LocalDate endDate,
                                @RequestParam(required = false) Long therapistId,
                                @RequestParam(required = false) String status,
                                @RequestParam(defaultValue = "newest") String sortOrder,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        Branch scope = branchAccess.scope(branchCode);
        Long branchId = scope == null ? null : scope.getId();
        AppointmentStatus statusValue = status == null || status.isBlank() ? null : parseStatus(status);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("วันที่เริ่มต้นต้องไม่อยู่หลังวันที่สิ้นสุด");
        }
        Sort.Direction direction = "oldest".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String keyword = query.trim().toLowerCase(java.util.Locale.ROOT);
        Page<Appointment> result = repository.search(branchId, date != null, date == null ? LocalDate.EPOCH : date,
                startDate != null, startDate == null ? LocalDate.EPOCH : startDate,
                endDate != null, endDate == null ? LocalDate.EPOCH : endDate, therapistId,
                statusValue != null, statusValue == null ? AppointmentStatus.Confirmed : statusValue,
                keyword, "%" + keyword + "%",
                PageRequest.of(page, size, Sort.by(direction, "appointmentDate")
                        .and(Sort.by(direction, "appointmentTime")).and(Sort.by(direction, "id"))));
        return new PageResponse(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Free start times of one therapist on one day; the service is optional (the branch default length is used). */
    @GetMapping("/availability")
    public AppointmentAvailabilityService.Availability availability(@RequestParam String branchCode,
                                                                    @RequestParam Long therapistId,
                                                                    @RequestParam LocalDate date,
                                                                    @RequestParam(required = false) Long serviceId,
                                                                    @RequestParam(required = false) Long excludeAppointmentId) {
        Branch branch = branchAccess.scope(branchCode);
        Staff therapist = staff.findById(therapistId).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ให้บริการ"));
        if (!branch.getId().equals(therapist.getBranch().getId())) {
            throw new IllegalArgumentException("ผู้ให้บริการต้องอยู่สาขาเดียวกับนัดหมาย");
        }
        Service svc = serviceId == null ? null : services.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
        return availability.availability(branch, therapist, svc, date, excludeAppointmentId);
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return toResponse(requireAppointmentAccess(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody CreateRequest request) {
        if (request.branchCode() == null || request.branchCode().isBlank()) {
            throw new IllegalArgumentException("ต้องระบุสาขา");
        }
        Branch branch = branchAccess.scope(request.branchCode());
        if (request.therapistId() == null || request.startAt() == null) {
            throw new IllegalArgumentException("ต้องระบุนักกายภาพและเวลานัด");
        }
        Staff therapist = staff.findById(request.therapistId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ให้บริการ"));
        if (!branch.getId().equals(therapist.getBranch().getId())) {
            throw new IllegalArgumentException("ผู้ให้บริการต้องอยู่สาขาเดียวกับนัดหมาย");
        }
        if (!branchAccess.isManager() && !therapist.getId().equals(branchAccess.currentStaffId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "นักกายภาพจองนัดได้เฉพาะเวลาว่างของตัวเอง");
        }
        OffsetDateTime local = request.startAt().atZoneSameInstant(CLINIC_ZONE).toOffsetDateTime();
        if (request.endAt() == null) {
            throw new IllegalArgumentException("ต้องระบุเวลาจบนัด");
        }
        java.time.LocalDateTime bookingEnd = request.endAt()
                .atZoneSameInstant(CLINIC_ZONE)
                .toLocalDateTime();

        if (!bookingEnd.isAfter(local.toLocalDateTime())) {
            throw new IllegalArgumentException(
                    "เวลาจบนัดต้องอยู่หลังเวลาเริ่มนัด");
        }
        AppointmentStatus initial = parseInitialStatus(request.status());
        Service chosen = request.serviceId() == null ? null : services.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
        availability.requireNoGoogleBusy(branch, local.toLocalDateTime(), bookingEnd);
        if ("NEW".equalsIgnoreCase(request.patientType())) {
            RequestedServiceType requested = request.requestedServiceType() == null || request.requestedServiceType().isBlank()
                    ? null : RequestedServiceType.valueOf(request.requestedServiceType().trim());
            String middleName = request.contactMiddleName() == null
                    ? null : request.contactMiddleName().trim();
            if (middleName != null && middleName.length() > 100) {
                throw new IllegalArgumentException("ชื่อกลางต้องไม่เกิน 100 ตัวอักษร");
            }
            Appointment appointment = service.bookNewPatient(
                    request.contactName(), request.contactPhone(), branch, therapist,
                    requested, chosen, null,
                    local.toLocalDate(), local.toLocalTime(),
                    bookingEnd, initial, actor.currentId());

            appointment.setContactMiddleName(
                    middleName == null || middleName.isBlank() ? null : middleName);
            return createdResponse(appointment, request.note());
        }
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        // The service is optional: an appointment can be made before anyone decides what will be done.
        Service svc = request.serviceId() == null ? null : services.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
        Appointment appointment = service.book(
                patient, branch, therapist, svc, null,
                local.toLocalDate(), local.toLocalTime(),
                bookingEnd, initial, actor.currentId());
        return createdResponse(appointment, request.note());
    }

    @PostMapping("/{id}/confirm")
    public Response confirm(@PathVariable Long id) {
        branchAccess.requireManager();
        requireAppointmentAccess(id);
        return toResponse(service.confirm(id, actor.currentId()));
    }

    @PostMapping("/{id}/link-patient")
    public Response linkPatient(@PathVariable Long id, @RequestBody LinkPatientRequest request) {
        branchAccess.requireManager();
        // Lock before the first entity load: otherwise a waiting transaction could keep a stale NEW snapshot.
        branchAccess.requireAccess(service.getForUpdate(id).getBranch());
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        return toResponse(service.linkRegisteredPatient(id, patient, actor.currentId()));
    }

    @PostMapping("/{id}/check-in")
    public Response checkIn(@PathVariable Long id) {
        branchAccess.requireManager();
        requireAppointmentAccess(id);
        visitService.checkInFromAppointment(id, actor.currentId());
        return toResponse(service.get(id));
    }

    @PostMapping("/{id}/start-service")
    public Response startService(@PathVariable Long id) {
        branchAccess.requireManager();
        requireAppointmentAccess(id);
        PatientVisit visit = requireVisit(id);
        visitService.startService(visit.getId(), actor.currentId());
        return toResponse(service.get(id));
    }

    @PostMapping("/{id}/complete")
    public Response complete(@PathVariable Long id) {
        branchAccess.requireManager();
        requireAppointmentAccess(id);
        PatientVisit visit = requireVisit(id);
        visitService.complete(visit.getId(), actor.currentId());
        return toResponse(service.get(id));
    }

    @PostMapping("/{id}/no-show")
    public Response noShow(@PathVariable Long id) {
        branchAccess.requireManager();
        requireAppointmentAccess(id);
        return toResponse(service.markNoShow(id, actor.currentId()));
    }

    @PostMapping("/{id}/cancel")
    public Response cancel(@PathVariable Long id, @RequestBody CancelRequest request) {
        branchAccess.requireManager();
        requireAppointmentAccess(id);
        return toResponse(service.cancel(id, request.reason(), actor.currentId()));
    }

    @PatchMapping("/{id}")
    public Response reschedule(@PathVariable Long id, @RequestBody RescheduleRequest request) {
        branchAccess.requireManager();
        requireAppointmentAccess(id);
        if (request.startAt() == null) throw new IllegalArgumentException("ต้องระบุเวลานัดใหม่");
        OffsetDateTime local = request.startAt().atZoneSameInstant(CLINIC_ZONE).toOffsetDateTime();
        if (request.endAt() == null) {
            throw new IllegalArgumentException("ต้องระบุเวลาจบนัด");
        }
        java.time.LocalDateTime bookingEnd = request.endAt()
                .atZoneSameInstant(CLINIC_ZONE)
                .toLocalDateTime();
        if (!bookingEnd.isAfter(local.toLocalDateTime())) {
            throw new IllegalArgumentException("เวลาจบนัดต้องอยู่หลังเวลาเริ่มนัด");
        }
        Appointment current = service.getForUpdate(id);
        branchAccess.requireAccess(current.getBranch());
        Branch branch = request.branchCode() == null || request.branchCode().isBlank()
                ? current.getBranch() : branchAccess.scope(request.branchCode());
        Staff therapist = request.therapistId() == null ? current.getStaff()
                : staff.findById(request.therapistId()).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ให้บริการ"));
        Service chosen = request.serviceId() == null ? current.getService()
                : services.findById(request.serviceId()).orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
        if (!branch.getId().equals(therapist.getBranch().getId())) {
            throw new IllegalArgumentException("นักกายภาพต้องอยู่สาขาเดียวกับนัดหมาย");
        }
        availability.requireNoGoogleBusy(branch, local.toLocalDateTime(), bookingEnd);
        Appointment appointment = service.reschedule(
                id, branch, therapist, chosen, local.toLocalDate(), local.toLocalTime(), bookingEnd, "เลื่อนนัดผ่าน API", actor.currentId()
        );
        if (request.note() != null) {
            if (!request.note().isNull() && !request.note().isTextual()) {
                throw new IllegalArgumentException("หมายเหตุต้องเป็นข้อความหรือ null");
            }
            appointment.setNotes(request.note().isNull() ? null : request.note().textValue());
        }
        return toResponse(appointment);
    }

    private static AppointmentStatus parseInitialStatus(String status) {
        if (status == null || status.isBlank()) return AppointmentStatus.Confirmed;
        return parseStatus(status);
    }

    private static String apiStatus(AppointmentStatus status) {
        return switch (status) {
            case InService -> "IN_SERVICE";
            case NoShow -> "NO_SHOW";
            default -> status.name().toUpperCase(java.util.Locale.ROOT);
        };
    }

    private static AppointmentStatus parseStatus(String text) {
        String value = text.trim();
        for (AppointmentStatus status : AppointmentStatus.values()) {
            if (apiStatus(status).equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) return status;
        }
        throw new IllegalArgumentException("Invalid appointment status: " + text);
    }

    private PatientVisit requireVisit(Long appointmentId) {
        return visits.findFirstByAppointmentIdOrderByIdDesc(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("ยังไม่มีการเช็คอินสำหรับนัดหมายนี้"));
    }

    private Appointment requireAppointmentAccess(Long id) {
        Appointment appointment = service.get(id);
        branchAccess.requireAccess(appointment.getBranch());
        return appointment;
    }

    private Response createdResponse(Appointment appointment, String note) {
        appointment.setNotes(note);
        return toResponse(repository.save(appointment));
    }

    private Response toResponse(Appointment appointment) {
        Instant startAt = appointment.getAppointmentDate().atTime(appointment.getAppointmentTime())
                .atZone(CLINIC_ZONE).toInstant();
        Patient patient = appointment.getPatient();
        PatientSummary patientSummary = patient == null
                ? new PatientSummary(null, null, appointment.getContactName(), appointment.getContactPhone())
                : new PatientSummary(patient.getId(), patient.getPatientHn(),
                java.util.stream.Stream.of(patient.getPatientFname(), patient.getPatientMiddleName(), patient.getPatientLname())
                        .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(" ")),
                patient.getPhone());
        Service svc = appointment.getService();
        int duration = Math.toIntExact(java.time.Duration.between(
                appointment.getAppointmentDate().atTime(appointment.getAppointmentTime()),
                appointment.getAppointmentEndAt()).toMinutes());
        ServiceSummary serviceSummary = svc == null
                ? new ServiceSummary(null, appointment.getRequestedServiceType() == null ? null
                : appointment.getRequestedServiceType().name(), duration)
                : new ServiceSummary(svc.getId(), svc.getServiceName(), duration);
        Instant endAt = appointment.getAppointmentEndAt()
                .atZone(CLINIC_ZONE).toInstant();
        return new Response(appointment.getId(), appointment.getBranch().getBranchCode(), patientSummary,
                new TherapistSummary(appointment.getStaff().getId(),
                        appointment.getStaff().getStaffFname() + " " + appointment.getStaff().getStaffLname()),
                serviceSummary, startAt, endAt, apiStatus(appointment.getAppointmentStatus()), appointment.getNotes(),
                history.findFirstByAppointmentIdAndToStatusOrderByChangedAtDescIdDesc(appointment.getId(), AppointmentStatus.Cancelled)
                        .map(AppointmentStatusHistory::getReason).orElse(null),
                appointment.getAppointmentPatientType().name(), appointment.getContactName(),
                appointment.getContactPhone(), appointment.getContactMiddleName());
    }
}
