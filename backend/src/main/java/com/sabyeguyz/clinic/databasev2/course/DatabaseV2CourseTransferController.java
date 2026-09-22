package com.sabyeguyz.clinic.databasev2.course;

import com.sabyeguyz.clinic.course.CourseTransferRequest;
import com.sabyeguyz.clinic.course.CourseTransferResponse;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api/course-transfers")
public class DatabaseV2CourseTransferController {

    private static final ZoneOffset BANGKOK_OFFSET = ZoneOffset.ofHours(7);

    private final DatabaseV2CourseRepository courseRepository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2CourseTransferController(DatabaseV2CourseRepository courseRepository, CurrentActorProvider actorProvider) {
        this.courseRepository = courseRepository;
        this.actorProvider = actorProvider;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CourseTransferResponse> transfer(@Valid @RequestBody CourseTransferRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);

        DatabaseV2CourseRepository.CourseTransferRecord record = courseRepository.transfer(
                request.sourcePurchaseId(), request.destinationPatientId(),
                request.quantity(), request.reason().trim(), actorId
        );

        return ResponseEntity.status(201).body(toResponse(record));
    }

    @GetMapping
    public List<CourseTransferResponse> history(@RequestParam(required = false) Long patientId) {
        return courseRepository.findTransferHistory(patientId).stream()
                .map(DatabaseV2CourseTransferController::toResponse)
                .toList();
    }

    public static CourseTransferResponse toResponse(DatabaseV2CourseRepository.CourseTransferRecord r) {
        return new CourseTransferResponse(
                r.id(),
                r.sourcePurchaseId(),
                r.destinationPurchaseId(),
                r.sourcePatientId(),
                r.sourcePatientHn(),
                r.sourcePatientName(),
                r.destinationPatientId(),
                r.destinationPatientHn(),
                r.destinationPatientName(),
                r.courseId(),
                r.branchCode(),
                r.quantity(),
                r.actorId(),
                r.reason(),
                r.createdAt().toInstant(BANGKOK_OFFSET)
        );
    }
}
