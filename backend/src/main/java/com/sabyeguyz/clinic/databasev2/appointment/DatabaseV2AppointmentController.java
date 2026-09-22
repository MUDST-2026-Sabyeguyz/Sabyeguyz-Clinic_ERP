package com.sabyeguyz.clinic.databasev2.appointment;

import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
import com.sabyeguyz.clinic.databasev2.catalog.DatabaseV2ServiceRepository;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2ConflictException;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import com.sabyeguyz.clinic.databasev2.patient.DatabaseV2PatientRepository;
import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api/appointments")
public class DatabaseV2AppointmentController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    private final DatabaseV2AppointmentRepository appointmentRepository;
    private final DatabaseV2PatientRepository patientRepository;
    private final DatabaseV2BranchRepository branchRepository;
    private final DatabaseV2StaffRepository staffRepository;
    private final DatabaseV2ServiceRepository serviceRepository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2AppointmentController(
            DatabaseV2AppointmentRepository appointmentRepository,
            DatabaseV2PatientRepository patientRepository,
            DatabaseV2BranchRepository branchRepository,
            DatabaseV2StaffRepository staffRepository,
            DatabaseV2ServiceRepository serviceRepository,
            CurrentActorProvider actorProvider) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.branchRepository = branchRepository;
        this.staffRepository = staffRepository;
        this.serviceRepository = serviceRepository;
        this.actorProvider = actorProvider;
    }

    public record CreateRequest(
            @NotBlank String branchCode,
            @NotNull Long patientId,
            @NotNull Long therapistId,
            @NotNull Long serviceId,
            @NotNull Instant startAt,
            String note,
            Long roomId
    ) {}

    public record RescheduleRequest(
            @NotNull Instant startAt,
            Long therapistId,
            String reason
    ) {}

    public record CancelRequest(
            @NotBlank String reason
    ) {}

    public record PatientSummary(Long id, String hn, String fullName) {}
    public record TherapistSummary(Long id, String fullName) {}
    public record ServiceSummary(Long id, String name, int durationMinutes) {}

    public record AppointmentResponse(
            Long id,
            String branchCode,
            PatientSummary patient,
            TherapistSummary therapist,
            ServiceSummary service,
            Instant startAt,
            Instant endAt,
            String status,
            String note,
            String cancelReason
    ) {}

    public record PageResponse(
            List<AppointmentResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages
    ) {}

    @GetMapping
    public PageResponse search(
            @RequestParam(required = false) String branchCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long therapistId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int limit = Math.max(1, Math.min(size, 100));
        int offset = Math.max(0, page) * limit;

        List<DatabaseV2AppointmentRepository.AppointmentModel> list = appointmentRepository.search(
                branchCode, date, therapistId, status, limit, offset
        );
        int total = appointmentRepository.countSearch(branchCode, date, therapistId, status);
        int totalPages = (int) Math.ceil((double) total / limit);

        List<AppointmentResponse> items = list.stream().map(DatabaseV2AppointmentController::toResponse).toList();
        return new PageResponse(items, page, limit, total, totalPages);
    }

    @GetMapping("/{id}")
    public AppointmentResponse getById(@PathVariable Long id) {
        return toResponse(find(id));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);

        DatabaseV2Branch branch = branchRepository.findByCode(request.branchCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + request.branchCode()));

        patientRepository.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย id " + request.patientId()));

        DatabaseV2StaffRepository.StaffModel staff = staffRepository.findById(request.therapistId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบนักกายภาพบำบัด id " + request.therapistId()));

        serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ id " + request.serviceId()));

        ZonedDateTime zdt = request.startAt().atZone(ZONE);
        LocalDate date = zdt.toLocalDate();
        LocalTime time = zdt.toLocalTime().withSecond(0).withNano(0);

        if (appointmentRepository.hasConflict(staff.id(), date, time, null)) {
            throw new DatabaseV2ConflictException("ช่วงเวลานี้มีนัดหมายอื่นอยู่แล้ว");
        }

        DatabaseV2AppointmentRepository.AppointmentModel created = appointmentRepository.create(
                request.patientId(), branch.getId(), staff.id(), request.serviceId(), request.roomId(),
                date, time, request.note(), actorId
        );
        AppointmentResponse response = toResponse(created);
        return ResponseEntity.created(URI.create("/api/appointments/" + response.id())).body(response);
    }

    @PostMapping("/{id}/check-in")
    @Transactional
    public AppointmentResponse checkIn(@PathVariable Long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2AppointmentRepository.AppointmentModel app = find(id);

        if (!"Confirmed".equals(app.status()) && !"Rescheduled".equals(app.status())) {
            throw new DatabaseV2ConflictException("สถานะนัดหมายไม่สามารถเช็คอินได้ (" + app.status() + ")");
        }

        appointmentRepository.updateStatus(id, app.status(), "Arrived", "Patient arrived", actorId);
        appointmentRepository.createPatientVisit(id, app.patientId(), app.branchId(), app.staffId(), actorId);

        return toResponse(find(id));
    }

    @PostMapping("/{id}/start-service")
    @Transactional
    public AppointmentResponse startService(@PathVariable Long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2AppointmentRepository.AppointmentModel app = find(id);

        if (!"Arrived".equals(app.status())) {
            throw new DatabaseV2ConflictException("ผู้ป่วยต้องเช็คอินก่อนเริ่มบริการ");
        }

        appointmentRepository.updateStatus(id, "Arrived", "InService", "Service started", actorId);
        appointmentRepository.updateVisitStatus(id, "InService", actorId);

        return toResponse(find(id));
    }

    @PostMapping("/{id}/complete")
    @Transactional
    public AppointmentResponse complete(@PathVariable Long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2AppointmentRepository.AppointmentModel app = find(id);

        if (!"InService".equals(app.status())) {
            throw new DatabaseV2ConflictException("บริการต้องเริ่มก่อนจึงจะเสร็จสิ้นได้");
        }

        appointmentRepository.updateStatus(id, "InService", "Completed", "Service completed", actorId);
        appointmentRepository.updateVisitStatus(id, "Completed", actorId);

        return toResponse(find(id));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public AppointmentResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2AppointmentRepository.AppointmentModel app = find(id);

        if ("Completed".equals(app.status()) || "Cancelled".equals(app.status()) || "NoShow".equals(app.status())) {
            throw new DatabaseV2ConflictException("ไม่สามารถยกเลิกนัดหมายในสถานะ " + app.status() + " ได้");
        }

        appointmentRepository.updateStatus(id, app.status(), "Cancelled", request.reason().trim(), actorId);
        appointmentRepository.updateVisitStatus(id, "Cancelled", actorId);

        return toResponse(find(id));
    }

    @PostMapping("/{id}/no-show")
    @Transactional
    public AppointmentResponse markNoShow(@PathVariable Long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2AppointmentRepository.AppointmentModel app = find(id);

        if (!"Confirmed".equals(app.status()) && !"Rescheduled".equals(app.status())) {
            throw new DatabaseV2ConflictException("ไม่สามารถระบุว่าไม่มาได้ในสถานะ " + app.status());
        }

        appointmentRepository.updateStatus(id, app.status(), "NoShow", "Patient did not show", actorId);
        return toResponse(find(id));
    }

    @PatchMapping("/{id}")
    @Transactional
    public AppointmentResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2AppointmentRepository.AppointmentModel app = find(id);

        if (!"Confirmed".equals(app.status()) && !"Rescheduled".equals(app.status())) {
            throw new DatabaseV2ConflictException("ไม่สามารถเลื่อนนัดหมายในสถานะ " + app.status() + " ได้");
        }

        Long newStaffId = request.therapistId() != null ? request.therapistId() : app.staffId();
        staffRepository.findById(newStaffId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบนักกายภาพบำบัด id " + newStaffId));

        ZonedDateTime zdt = request.startAt().atZone(ZONE);
        LocalDate newDate = zdt.toLocalDate();
        LocalTime newTime = zdt.toLocalTime().withSecond(0).withNano(0);

        if (appointmentRepository.hasConflict(newStaffId, newDate, newTime, id)) {
            throw new DatabaseV2ConflictException("ช่วงเวลานี้มีนัดหมายอื่นอยู่แล้ว");
        }

        String reason = request.reason() != null && !request.reason().isBlank() ? request.reason().trim() : "Rescheduled by staff";
        appointmentRepository.reschedule(id, newStaffId, newDate, newTime, app.status(), reason, actorId);

        return toResponse(find(id));
    }

    private DatabaseV2AppointmentRepository.AppointmentModel find(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบนัดหมาย"));
    }

    public static AppointmentResponse toResponse(DatabaseV2AppointmentRepository.AppointmentModel m) {
        ZonedDateTime startZdt = m.date().atTime(m.time()).atZone(ZONE);
        Instant startAt = startZdt.toInstant();
        Instant endAt = startZdt.plusMinutes(m.durationMinutes()).toInstant();

        String apiStatus = switch (m.status()) {
            case "Confirmed" -> "CONFIRMED";
            case "Arrived" -> "ARRIVED";
            case "InService" -> "IN_SERVICE";
            case "Completed" -> "COMPLETED";
            case "Cancelled" -> "CANCELLED";
            case "Rescheduled" -> "RESCHEDULED";
            case "NoShow" -> "NO_SHOW";
            default -> m.status();
        };

        return new AppointmentResponse(
                m.id(),
                m.branchCode(),
                new PatientSummary(m.patientId(), m.patientHn(), m.patientFullName()),
                new TherapistSummary(m.staffId(), m.staffFullName()),
                new ServiceSummary(m.serviceId(), m.serviceName(), m.durationMinutes()),
                startAt,
                endAt,
                apiStatus,
                m.note(),
                m.cancelReason()
        );
    }
}
