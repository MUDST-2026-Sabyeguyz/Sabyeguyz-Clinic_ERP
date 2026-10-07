package com.sabyeguyz.clinic.databasev2.treatment;

import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/treatments")
@Profile("database-v2")
public class DatabaseV2TreatmentController {

    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    public record HistoryResponse(
            Long id, Long patientId, Long appointmentId, Long staffId, Long serviceId,
            String service, String note, LocalDateTime createdAt) {}

    public record HistoryPageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record Request(
            @NotNull Long patientId,
            Long appointmentId,
            Long staffId,
            @NotNull Long serviceId,
            @NotBlank String note
    ) {}

    public record Response(
            Long id,
            Long patientId,
            Long appointmentId,
            Long staffId,
            Long serviceId,
            String service,
            String note
    ) {}

    public record ServiceResponse(
            Long id,
            String code,
            String name,
            int durationMinutes,
            BigDecimal price
    ) {}

    private final DatabaseV2TreatmentRepository treatmentRepository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2TreatmentController(DatabaseV2TreatmentRepository treatmentRepository, CurrentActorProvider actorProvider) {
        this.treatmentRepository = treatmentRepository;
        this.actorProvider = actorProvider;
    }

    @GetMapping("/services")
    public List<ServiceResponse> serviceList() {
        return treatmentRepository.findActiveServices().stream()
                .map(s -> new ServiceResponse(s.id(), s.code(), s.name(), s.durationMinutes(), s.price()))
                .toList();
    }

    @GetMapping("/patients/{patientId}")
    public List<Response> list(@PathVariable Long patientId) {
        return treatmentRepository.findByPatientId(patientId).stream()
                .map(t -> new Response(t.id(), t.patientId(), t.appointmentId(), t.staffId(), t.serviceId(), t.serviceName(), t.note()))
                .toList();
    }

    @GetMapping("/patients/{patientId}/history")
    public HistoryPageResponse<HistoryResponse> history(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!treatmentRepository.patientExists(patientId)) {
            throw new DatabaseV2NotFoundException("ไม่พบผู้ป่วย id " + patientId);
        }
        if (page < 0) throw new IllegalArgumentException("page ต้องไม่น้อยกว่า 0");
        if (size < 1 || size > MAX_HISTORY_PAGE_SIZE) {
            throw new IllegalArgumentException("size ต้องอยู่ระหว่าง 1 ถึง " + MAX_HISTORY_PAGE_SIZE);
        }
        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException tooLarge) {
            throw new IllegalArgumentException("page เกินขอบเขตที่รองรับ", tooLarge);
        }
        int total = treatmentRepository.countByPatientId(patientId);
        int totalPages = (int) Math.ceil((double) total / size);
        List<HistoryResponse> items = treatmentRepository.findByPatientId(patientId, size, offset).stream()
                .map(t -> new HistoryResponse(t.id(), t.patientId(), t.appointmentId(), t.staffId(), t.serviceId(), t.serviceName(), t.note(), t.createdAt()))
                .toList();
        return new HistoryPageResponse<>(items, page, size, total, totalPages);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2TreatmentRepository.TreatmentRecord t = treatmentRepository.createTreatment(
                request.patientId(),
                request.appointmentId(),
                request.staffId(),
                request.serviceId(),
                request.note(),
                actorId
        );
        return new Response(t.id(), t.patientId(), t.appointmentId(), t.staffId(), t.serviceId(), t.serviceName(), t.note());
    }
}
