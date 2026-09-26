package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
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
@RestController
@RequestMapping("/api/course-transfers")
public class CourseTransferController {
    private final CourseTransferService service;
    private final CourseTransferRepository repository;
    private final CourseRepository courses;
    private final PatientRepository patients;
    private final CurrentActor actor;

    public CourseTransferController(CourseTransferService service, CourseTransferRepository repository,
                                     CourseRepository courses, PatientRepository patients, CurrentActor actor) {
        this.service = service;
        this.repository = repository;
        this.courses = courses;
        this.patients = patients;
        this.actor = actor;
    }

    public record Request(Long sourcePurchaseId, Long destinationPatientId, int quantity, String reason) {}

    public record Response(Long id, Long sourcePatientId, String sourcePatientHn, String sourcePatientName,
                            Long destinationPatientId, String destinationPatientHn, String destinationPatientName,
                            String branchCode, Long courseId, int quantity, String reason, Instant createdAt) {}

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
        Patient from = course.getReceipt().getPatient();
        Patient to = patients.findById(request.destinationPatientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วยปลายทาง"));
        CourseTransfer transfer = service.transfer(course, from, to, request.quantity(), request.reason(), actor.currentId());
        return toResponse(transfer);
    }

    private static Response toResponse(CourseTransfer transfer) {
        Patient from = transfer.getFromPatient();
        Patient to = transfer.getToPatient();
        Course course = transfer.getCourse();
        return new Response(transfer.getId(), from.getId(), from.getPatientHn(),
                from.getPatientFname() + " " + from.getPatientLname(), to.getId(), to.getPatientHn(),
                to.getPatientFname() + " " + to.getPatientLname(),
                course.getReceipt().getBranch().getBranchCode(), course.getId(), transfer.getQtyTransferred(),
                transfer.getRemark(), transfer.getTransferCreateAt());
    }
}
