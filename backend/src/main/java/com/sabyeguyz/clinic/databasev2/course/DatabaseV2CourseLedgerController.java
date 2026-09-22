package com.sabyeguyz.clinic.databasev2.course;

import com.sabyeguyz.clinic.course.CourseLedgerResponse;
import com.sabyeguyz.clinic.course.CourseMovementResponse;
import com.sabyeguyz.clinic.course.CourseMovementType;
import com.sabyeguyz.clinic.course.CoursePurchaseRequest;
import com.sabyeguyz.clinic.course.CoursePurchaseResponse;
import com.sabyeguyz.clinic.course.CoursePurchaseStatus;
import com.sabyeguyz.clinic.course.CourseReasonRequest;
import com.sabyeguyz.clinic.course.CourseUsageRequest;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
import com.sabyeguyz.clinic.databasev2.catalog.DatabaseV2ServiceRepository;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import com.sabyeguyz.clinic.databasev2.patient.DatabaseV2PatientRepository;
import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api")
public class DatabaseV2CourseLedgerController {

    private static final ZoneOffset BANGKOK_OFFSET = ZoneOffset.ofHours(7);

    private final DatabaseV2CourseRepository courseRepository;
    private final DatabaseV2PatientRepository patientRepository;
    private final DatabaseV2BranchRepository branchRepository;
    private final DatabaseV2StaffRepository staffRepository;
    private final DatabaseV2ServiceRepository serviceRepository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2CourseLedgerController(
            DatabaseV2CourseRepository courseRepository,
            DatabaseV2PatientRepository patientRepository,
            DatabaseV2BranchRepository branchRepository,
            DatabaseV2StaffRepository staffRepository,
            DatabaseV2ServiceRepository serviceRepository,
            CurrentActorProvider actorProvider) {
        this.courseRepository = courseRepository;
        this.patientRepository = patientRepository;
        this.branchRepository = branchRepository;
        this.staffRepository = staffRepository;
        this.serviceRepository = serviceRepository;
        this.actorProvider = actorProvider;
    }

    @PostMapping("/course-purchases")
    @Transactional
    public ResponseEntity<CoursePurchaseResponse> purchase(@Valid @RequestBody CoursePurchaseRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);

        patientRepository.findById(request.patientId())
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ป่วย id " + request.patientId()));

        DatabaseV2Branch branch = branchRepository.findByCode(request.branchCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + request.branchCode()));

        DatabaseV2ServiceRepository.ServiceModel service = serviceRepository.findById(request.courseId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส id " + request.courseId()));

        if (!service.active()) {
            throw new IllegalArgumentException("คอร์ส " + service.code() + " ถูกปิดการใช้งาน");
        }

        Long sellerId = null;
        if (request.sellerEmployeeId() != null) {
            DatabaseV2StaffRepository.StaffModel staff = staffRepository.findById(request.sellerEmployeeId())
                    .orElseThrow(() -> new IllegalArgumentException("ไม่พบพนักงานขาย id " + request.sellerEmployeeId()));
            sellerId = staff.id();
        }

        DatabaseV2CourseRepository.CoursePurchaseRecord record = courseRepository.purchase(
                request.patientId(), service.id(), branch.getId(), branch.getCode(), sellerId, actorId
        );

        CoursePurchaseResponse response = toResponse(record);
        return ResponseEntity.created(URI.create("/api/course-purchases/" + response.id())).body(response);
    }

    @GetMapping("/patients/{patientId}/courses")
    public List<CoursePurchaseResponse> listByPatient(@PathVariable Long patientId) {
        patientRepository.findById(patientId)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ป่วย id " + patientId));

        return courseRepository.findByPatientId(patientId).stream()
                .map(DatabaseV2CourseLedgerController::toResponse)
                .toList();
    }

    @GetMapping("/course-purchases/{id}")
    public CourseLedgerResponse ledger(@PathVariable Long id) {
        DatabaseV2CourseRepository.CoursePurchaseRecord record = courseRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบคอร์ส id " + id));

        List<DatabaseV2CourseRepository.CourseMovementRecord> movements = courseRepository.findMovements(id);
        List<CourseMovementResponse> movementResponses = movements.stream().map(m -> {
            CourseMovementType type = switch (m.entryType()) {
                case "Purchase" -> CourseMovementType.PURCHASE;
                case "Treatment" -> CourseMovementType.USAGE;
                case "TransferOut" -> CourseMovementType.TRANSFER_OUT;
                case "TransferIn" -> CourseMovementType.TRANSFER_IN;
                default -> CourseMovementType.REVERSAL;
            };
            return new CourseMovementResponse(
                    m.id(),
                    type,
                    m.qtyChange(),
                    m.balanceAfter(),
                    m.remark(),
                    "APPOINTMENT".equals(m.referenceType()) ? m.referenceId() : null,
                    "MOVEMENT_REVERSE".equals(m.referenceType()) ? m.referenceId() : null,
                    m.createdAt().toInstant(BANGKOK_OFFSET)
            );
        }).toList();

        return new CourseLedgerResponse(toResponse(record), movementResponses, List.of());
    }

    @PostMapping("/course-usages")
    @Transactional
    public ResponseEntity<CoursePurchaseResponse> use(@Valid @RequestBody CourseUsageRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);

        DatabaseV2CourseRepository.CoursePurchaseRecord updated = courseRepository.consumeSession(
                request.coursePurchaseId(), request.appointmentId(), request.note(), request.treatingStaffId(), actorId
        );

        return ResponseEntity.status(201).body(toResponse(updated));
    }

    @PostMapping("/course-movements/{movementId}/reverse")
    @Transactional
    public CoursePurchaseResponse reverseUsage(
            @PathVariable Long movementId,
            @Valid @RequestBody CourseReasonRequest request,
            Authentication auth
    ) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2CourseRepository.CoursePurchaseRecord updated = courseRepository.reverseUsage(
                movementId, request.reason().trim(), actorId
        );
        return toResponse(updated);
    }

    @PostMapping("/course-purchases/{id}/cancel")
    @Transactional
    public CoursePurchaseResponse cancel(
            @PathVariable Long id,
            @Valid @RequestBody CourseReasonRequest request,
            Authentication auth
    ) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2CourseRepository.CoursePurchaseRecord updated = courseRepository.cancel(
                id, request.reason().trim(), actorId
        );
        return toResponse(updated);
    }

    public static CoursePurchaseResponse toResponse(DatabaseV2CourseRepository.CoursePurchaseRecord r) {
        Instant now = Instant.now();
        Instant expiresAt = r.expiryDate().atTime(23, 59, 59).toInstant(BANGKOK_OFFSET);
        boolean expired = now.isAfter(expiresAt);
        boolean usable = !expired && "Active".equals(r.status()) && r.remainingVisits() > 0;

        CoursePurchaseStatus status = "Cancelled".equals(r.status())
                ? CoursePurchaseStatus.CANCELLED
                : CoursePurchaseStatus.ACTIVE;

        return new CoursePurchaseResponse(
                r.id(),
                new CoursePurchaseResponse.PatientSummary(r.patientId(), r.patientHn(), r.patientFullName()),
                r.branchCode(),
                new CoursePurchaseResponse.CourseSummary(r.serviceId(), r.serviceCode(), r.courseName()),
                r.price(),
                r.totalVisits(),
                r.bonusVisits(),
                r.totalVisits() + r.bonusVisits(),
                r.visitsUsed(),
                r.remainingVisits(),
                r.createdAt().toInstant(BANGKOK_OFFSET),
                expiresAt,
                expired,
                usable,
                status,
                r.commissionStatus(),
                r.lockedCommissionRate(),
                r.totalCourseCommissionPool(),
                r.commissionAllocationPerVisit()
        );
    }
}
