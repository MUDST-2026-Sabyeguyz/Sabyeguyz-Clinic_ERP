package com.sabyeguyz.clinic.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services")
public class ServiceManagementController {
    private final ServiceItemRepository repository;
    public ServiceManagementController(ServiceItemRepository repository) { this.repository = repository; }

    public record Request(@NotBlank String code, @NotBlank String name, @Min(1) int defaultDurationMinutes,
                          @NotNull @DecimalMin("0.0") BigDecimal price, Boolean active) {}
    public record Response(Long id, String code, String name, int defaultDurationMinutes, BigDecimal price, boolean active) {}

    @GetMapping
    List<Response> list() { return repository.findAll().stream().map(ServiceManagementController::out).toList(); }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    Response create(@Valid @RequestBody Request request) {
        String code = request.code().trim().toUpperCase();
        if (repository.findByCode(code).isPresent()) throw new IllegalArgumentException("รหัสบริการซ้ำ");
        ServiceItem item = new ServiceItem(code, request.name().trim(), request.defaultDurationMinutes(), request.price());
        if (request.active() != null) {
            if (request.active()) item.activate(); else item.deactivate();
        }
        return out(repository.save(item));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        ServiceItem item = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
        String code = request.code().trim().toUpperCase();
        if (repository.findByCode(code).filter(found -> !found.getId().equals(id)).isPresent()) throw new IllegalArgumentException("รหัสบริการซ้ำ");
        item.update(code, request.name().trim(), request.defaultDurationMinutes(), request.price());
        if (request.active() != null) {
            if (request.active()) item.activate(); else item.deactivate();
        }
        return out(repository.save(item));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable Long id) {
        ServiceItem item = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
        item.deactivate(); repository.save(item);
    }

    private static Response out(ServiceItem item) {
        return new Response(item.getId(), item.getCode(), item.getName(), item.getDefaultDurationMinutes(), item.getPrice(), item.isActive());
    }
}
