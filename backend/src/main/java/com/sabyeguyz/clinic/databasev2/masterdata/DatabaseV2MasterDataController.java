package com.sabyeguyz.clinic.databasev2.masterdata;

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
@RequestMapping("/api/master-data")
public class DatabaseV2MasterDataController {

    private final DatabaseV2MasterDataRepository repository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2MasterDataController(DatabaseV2MasterDataRepository repository, CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.actorProvider = actorProvider;
    }

    public record Request(
            @NotBlank String category,
            String code,
            @NotBlank String name,
            Boolean active
    ) {}

    public record Response(
            Long id,
            String category,
            String code,
            String name,
            boolean active
    ) {}

    @GetMapping
    public List<Response> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
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
        boolean active = request.active() == null || request.active();
        String name = request.name().trim();
        repository.findByCategory(request.category()).stream()
                .filter(item -> item.name().equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(item -> {
                    throw new IllegalArgumentException("รหัสข้อมูลพื้นฐานซ้ำในหมวดเดียวกัน");
                });
        DatabaseV2MasterDataRepository.MasterDataRecord created = repository.create(
                request.category(), name, 0, active, actorId
        );
        return toResponse(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2MasterDataRepository.MasterDataRecord existing = find(id);
        boolean active = request.active() != null ? request.active() : existing.active();
        String name = request.name().trim();
        repository.findByCategory(request.category()).stream()
                .filter(item -> !item.id().equals(id) && item.name().equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(item -> {
                    throw new IllegalArgumentException("รหัสข้อมูลพื้นฐานซ้ำในหมวดเดียวกัน");
                });
        DatabaseV2MasterDataRepository.MasterDataRecord updated = repository.update(
                id, name, existing.sortOrder(), active, actorId
        );
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

    private DatabaseV2MasterDataRepository.MasterDataRecord find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบข้อมูลพื้นฐาน"));
    }

    private Response toResponse(DatabaseV2MasterDataRepository.MasterDataRecord r) {
        return new Response(r.id(), r.category(), r.code(), r.name(), r.active());
    }
}
