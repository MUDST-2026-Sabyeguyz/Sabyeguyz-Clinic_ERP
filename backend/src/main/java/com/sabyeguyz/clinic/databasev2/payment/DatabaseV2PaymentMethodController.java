package com.sabyeguyz.clinic.databasev2.payment;

import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api/payment-methods")
public class DatabaseV2PaymentMethodController {

    private final DatabaseV2PaymentMethodRepository repository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2PaymentMethodController(
            DatabaseV2PaymentMethodRepository repository,
            CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.actorProvider = actorProvider;
    }

    public record Request(@NotBlank String code, @NotBlank String name, Boolean active) {}
    public record Response(Long id, String code, String name, boolean active) {}

    @GetMapping
    public List<Response> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        String name = request.name().trim();
        if (repository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("รหัสช่องทางชำระเงินซ้ำ");
        }
        boolean active = request.active() == null || request.active();
        DatabaseV2PaymentMethodRepository.PaymentMethodRecord created = repository.create(name, active, actorId);
        return toResponse(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2PaymentMethodRepository.PaymentMethodRecord existing = find(id);
        String name = request.name().trim();
        repository.findByName(name).filter(m -> !m.id().equals(id)).ifPresent(m -> {
            throw new IllegalArgumentException("รหัสช่องทางชำระเงินซ้ำ");
        });
        boolean active = request.active() != null ? request.active() : existing.active();
        DatabaseV2PaymentMethodRepository.PaymentMethodRecord updated = repository.update(id, name, active, actorId);
        return toResponse(updated);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('MANAGER')")
    public Response activate(@PathVariable Long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        find(id);
        repository.setStatus(id, true, actorId);
        return toResponse(find(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        find(id);
        repository.setStatus(id, false, actorId);
    }

    private DatabaseV2PaymentMethodRepository.PaymentMethodRecord find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบช่องทางชำระเงิน"));
    }

    private Response toResponse(DatabaseV2PaymentMethodRepository.PaymentMethodRecord r) {
        return new Response(r.id(), r.code(), r.name(), r.active());
    }
}
