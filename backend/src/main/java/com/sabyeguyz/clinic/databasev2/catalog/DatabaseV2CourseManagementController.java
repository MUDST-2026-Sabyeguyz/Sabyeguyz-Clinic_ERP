package com.sabyeguyz.clinic.databasev2.catalog;

import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api/courses")
public class DatabaseV2CourseManagementController {

    private final DatabaseV2ServiceRepository repository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2CourseManagementController(DatabaseV2ServiceRepository repository, CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.actorProvider = actorProvider;
    }

    public record Request(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String name,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            @Min(1) int sessions,
            @Min(0) int bonusSessions,
            @Min(1) int validityDays,
            Boolean active
    ) {}

    public record Response(
            Long id,
            String code,
            String name,
            BigDecimal price,
            int sessions,
            int bonusSessions,
            int validityDays,
            boolean active
    ) {}

    @GetMapping
    public List<Response> list() {
        return repository.findAll().stream()
                .filter(s -> "Course".equalsIgnoreCase(s.type()))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return toResponse(find(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        String code = request.code().trim().toUpperCase();
        if (repository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("รหัส Course ซ้ำ");
        }
        boolean active = request.active() == null || request.active();
        DatabaseV2ServiceRepository.ServiceModel created = repository.create(
                code, request.name().trim(), "Course", request.price(),
                request.sessions(), request.bonusSessions(), request.validityDays(),
                active, actorId
        );
        return toResponse(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2ServiceRepository.ServiceModel existing = find(id);
        String code = request.code().trim().toUpperCase();
        repository.findByCode(code).filter(s -> !s.id().equals(id)).ifPresent(s -> {
            throw new IllegalArgumentException("รหัส Course ซ้ำ");
        });
        boolean active = request.active() != null ? request.active() : existing.active();
        DatabaseV2ServiceRepository.ServiceModel updated = repository.update(
                id, code, request.name().trim(), "Course", request.price(),
                request.sessions(), request.bonusSessions(), request.validityDays(),
                active, actorId
        );
        return toResponse(updated);
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
                .filter(s -> "Course".equalsIgnoreCase(s.type()))
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบ Course"));
    }

    private Response toResponse(DatabaseV2ServiceRepository.ServiceModel s) {
        return new Response(
                s.id(),
                s.code(),
                s.name(),
                s.price(),
                s.totalVisits() != null ? s.totalVisits() : 1,
                s.bonusVisits() != null ? s.bonusVisits() : 0,
                s.validityDays() != null ? s.validityDays() : 365,
                s.active()
        );
    }
}
