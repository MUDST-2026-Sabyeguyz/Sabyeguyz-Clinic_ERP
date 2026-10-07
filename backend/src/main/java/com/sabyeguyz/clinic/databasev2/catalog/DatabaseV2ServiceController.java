package com.sabyeguyz.clinic.databasev2.catalog;

import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api/services")
public class DatabaseV2ServiceController {

    private final DatabaseV2ServiceRepository repository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2ServiceController(DatabaseV2ServiceRepository repository, CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.actorProvider = actorProvider;
    }

    public record Request(
            @NotBlank String code,
            @NotBlank String name,
            @Min(1) Integer defaultDurationMinutes,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            Boolean active,
            String type
    ) {}

    public record Response(
            Long id,
            String code,
            String name,
            int defaultDurationMinutes,
            BigDecimal price,
            boolean active
    ) {}

    @GetMapping
    public List<Response> list() {
        return repository.findAll().stream()
                .filter(s -> !"Course".equals(s.type()))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return toResponse(find(id));
    }

    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        String code = request.code().trim().toUpperCase();
        if (repository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("รหัสบริการซ้ำ");
        }
        String type = request.type() != null && !request.type().isBlank() ? request.type().trim() : "SingleVisit";
        boolean active = request.active() == null || request.active();
        DatabaseV2ServiceRepository.ServiceModel created = repository.create(
                code, request.name().trim(), type, request.price(), 1, 0, 365, active, actorId
        );
        repository.setDuration(created.id(), request.defaultDurationMinutes() == null ? 60 : request.defaultDurationMinutes());
        return toResponse(find(created.id()));
    }

    @PutMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2ServiceRepository.ServiceModel existing = find(id);
        String code = request.code().trim().toUpperCase();
        repository.findByCode(code).filter(s -> !s.id().equals(id)).ifPresent(s -> {
            throw new IllegalArgumentException("รหัสบริการซ้ำ");
        });
        String type = request.type() != null && !request.type().isBlank() ? request.type().trim() : existing.type();
        boolean active = request.active() != null ? request.active() : existing.active();
        DatabaseV2ServiceRepository.ServiceModel updated = repository.update(
                id, code, request.name().trim(), type, request.price(), existing.totalVisits(),
                existing.bonusVisits(), existing.validityDays(), active, actorId
        );
        repository.setDuration(updated.id(), request.defaultDurationMinutes() == null ? existing.durationMinutes() : request.defaultDurationMinutes());
        return toResponse(find(updated.id()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        find(id);
        repository.setStatus(id, false, actorId);
    }

    private DatabaseV2ServiceRepository.ServiceModel find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบบริการ"));
    }

    private Response toResponse(DatabaseV2ServiceRepository.ServiceModel s) {
        return new Response(
                s.id(),
                s.code(),
                s.name(),
                s.durationMinutes(),
                s.price(),
                s.active()
        );
    }
}
