package com.sabyeguyz.clinicv2.catalog;

import org.springframework.security.access.prepost.PreAuthorize;
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
 * Matches frontend/src/api/client.ts's serviceManagementApi (GET/POST/PUT/
 * DELETE /api/services) — Assessment/SingleVisit services only. Course
 * templates go through {@link CourseTemplateController} (/api/courses)
 * even though both are the same v2 Service entity underneath.
 */
@PreAuthorize("hasRole('MANAGER')")
@RestController
@RequestMapping("/api/services")
public class ServiceManagementController {
    private final ServiceCatalogService service;
    private final ServiceRepository repository;
    private final CurrentActor actor;

    public ServiceManagementController(ServiceCatalogService service, ServiceRepository repository, CurrentActor actor) {
        this.service = service;
        this.repository = repository;
        this.actor = actor;
    }

    public record Request(@NotBlank String code, @NotBlank String name, int defaultDurationMinutes,
                           @NotNull BigDecimal price) {}

    public record Response(Long id, String code, String name, int defaultDurationMinutes, BigDecimal price,
                            boolean active) {}

    @GetMapping
    public List<Response> list() {
        return repository.findAll().stream()
                .filter(s -> s.getServiceType() == ServiceType.SingleVisit)
                .map(ServiceManagementController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request request) {
        Service created = service.create(request.code(), request.name(), ServiceType.SingleVisit, request.price(),
                null, null, null, actor.currentId());
        return toResponse(created);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        Service existing = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
        existing.setServiceName(request.name());
        existing.setServicePrice(request.price());
        return toResponse(repository.save(existing));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id, actor.currentId());
    }

    private static Response toResponse(Service s) {
        return new Response(s.getId(), s.getServiceCode(), s.getServiceName(), 0, s.getServicePrice(), s.isServiceStatus());
    }
}
