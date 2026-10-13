package com.sabyeguyz.clinicv2.catalog;

import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.enums.ServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's courseApi's template CRUD
 * (GET/POST/PUT/DELETE /api/courses) — these are Course templates (v2
 * Service rows with serviceType = Course), NOT sold Course instances. A
 * sold instance is v2's own Course entity, exposed through
 * {@link com.sabyeguyz.clinicv2.sale.SalesTransactionController}'s
 * course-purchase endpoints instead.
 */
@org.springframework.transaction.annotation.Transactional
@RestController
@RequestMapping("/api/courses")
public class CourseTemplateController {
    private final ServiceCatalogService service;
    private final ServiceRepository repository;
    private final CurrentActor actor;

    public CourseTemplateController(ServiceCatalogService service, ServiceRepository repository, CurrentActor actor) {
        this.service = service;
        this.repository = repository;
        this.actor = actor;
    }

    public record Request(@NotBlank String code, @NotBlank String name, @NotNull BigDecimal price,
                           @NotNull Integer sessions, Integer bonusSessions, @NotNull Integer validityDays) {}

    public record Response(Long id, String code, String name, BigDecimal price, int sessions, int bonusSessions,
                            int validityDays, boolean active) {}

    @GetMapping
    public List<Response> list() {
        return repository.findByServiceTypeOrderByServiceName(ServiceType.Course).stream()
                .map(CourseTemplateController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return toResponse(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request request) {
        Service created = service.create(request.code(), request.name(), ServiceType.Course, request.price(),
                request.sessions(), request.bonusSessions() == null ? 0 : request.bonusSessions(),
                request.validityDays(), actor.currentId());
        return toResponse(created);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        Service existing = find(id);
        existing.setServiceName(request.name());
        existing.setServicePrice(request.price());
        existing.setDefaultTotalVisits(request.sessions());
        existing.setDefaultBonusVisits(request.bonusSessions() == null ? 0 : request.bonusSessions());
        existing.setDefaultValidityDays(request.validityDays());
        return toResponse(repository.save(existing));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id, actor.currentId());
    }

    private Service find(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
    }

    private static Response toResponse(Service s) {
        return new Response(s.getId(), s.getServiceCode(), s.getServiceName(), s.getServicePrice(),
                s.getDefaultTotalVisits() == null ? 0 : s.getDefaultTotalVisits(),
                s.getDefaultBonusVisits() == null ? 0 : s.getDefaultBonusVisits(),
                s.getDefaultValidityDays() == null ? 0 : s.getDefaultValidityDays(), s.isServiceStatus());
    }
}
