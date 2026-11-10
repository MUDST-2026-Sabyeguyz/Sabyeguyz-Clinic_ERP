package com.sabyeguyz.clinicv2.course;

import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
import com.sabyeguyz.clinicv2.role.PermissionService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's courseApi.transfer/transferHistory
 * (POST/GET /api/course-transfers). Known gap: the request only has
 * sourcePurchaseId (a Course id), not an explicit source patient, so this
 * always transfers from the Course's original buyer (Receipt.patient) -
 * transferring away from a Shared Member isn't reachable through this
 * endpoint shape.
 */
@Transactional
@RestController
@RequestMapping("/api/course-transfers")
public class CourseTransferController {
    private final CourseTransferService service;
    private final CourseTransferRepository repository;
    private final CourseRepository courses;
    private final PatientRepository patients;
    private final PermissionService permissions;
    private final CurrentActor actor;
    private final BranchAccessPolicy branchAccess;
    private final com.sabyeguyz.clinicv2.audit.AuditLogService audit;

    public CourseTransferController(CourseTransferService service, CourseTransferRepository repository,
                                    CourseRepository courses, PatientRepository patients,
                                    PermissionService permissions, CurrentActor actor,
                                    com.sabyeguyz.clinicv2.audit.AuditLogService audit,
                                    BranchAccessPolicy branchAccess) {
        this.service = service;
        this.repository = repository;
        this.courses = courses;
        this.patients = patients;
        this.permissions = permissions;
        this.actor = actor;
        this.audit = audit;
        this.branchAccess = branchAccess;
    }

    public record Request(Long sourcePurchaseId, Long destinationPatientId, int quantity, String reason) {}

    public record ReverseRequest(String reason) {}

    public record Response(Long id, Long sourcePatientId, String sourcePatientHn, String sourcePatientName,
                            Long destinationPatientId, String destinationPatientHn, String destinationPatientName,
                            String branchCode, Long courseId, int quantity, String reason, Instant createdAt,
                            String status) {}

    @GetMapping
    public List<Response> history(@RequestParam(required = false) Long patientId) {
        List<CourseTransfer> transfers = patientId == null
                ? repository.findAllByOrderByIdDesc()
                : repository.findByFromPatientIdOrToPatientIdOrderByIdDesc(patientId, patientId);
        return transfers.stream().map(CourseTransferController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody Request request) {
        Course course = courses.findById(request.sourcePurchaseId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
        branchAccess.requireAccess(course.getBranch(), "โอนสิทธิ์ได้เฉพาะคอร์สของสาขาตัวเอง");
        Patient from = course.getPurchaserPatient();
        Patient to = patients.findById(request.destinationPatientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วยปลายทาง"));
        CourseTransfer transfer = service.transfer(course, from, to, request.quantity(), request.reason(), actor.currentId());
        audit.record("course_transfer", String.valueOf(transfer.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.INSERT, null,
                request.quantity() + " visits: course " + course.getId() + " patient " + from.getId() + " -> " + to.getId(),
                actor.currentId());
        return toResponse(transfer);
    }

    @PostMapping("/{id}/reverse")
    public Response reverse(@PathVariable Long id, @RequestBody ReverseRequest request) {
        CourseTransfer transfer = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการโอน"));
        var permission = permissions.requireAllowed("COURSE_REVERSE", actor.currentId());
        branchAccess.requireAccess(
                transfer.getCourse().getBranch(),
                "ย้อนรายการได้เฉพาะสาขาประจำของตัวเอง");
        CourseTransfer reversed = service.reverse(transfer, permission, request.reason(), actor.currentId());
        audit.record("course_transfer", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE, "Completed", "Reversed: " + request.reason(),
                actor.currentId());
        return toResponse(reversed);
    }

    private static Response toResponse(CourseTransfer transfer) {
        Patient from = transfer.getFromPatient();
        Patient to = transfer.getToPatient();
        Course course = transfer.getCourse();
        return new Response(transfer.getId(), from.getId(), from.getPatientHn(),
                from.getPatientFname() + " " + from.getPatientLname(), to.getId(), to.getPatientHn(),
                to.getPatientFname() + " " + to.getPatientLname(),
                course.getReceipt().getBranch().getBranchCode(), course.getId(), transfer.getQtyTransferred(),
                transfer.getRemark(), transfer.getTransferCreateAt(), transfer.getTransferStatus().name());
    }
}
