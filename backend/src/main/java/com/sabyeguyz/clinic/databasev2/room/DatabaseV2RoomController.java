package com.sabyeguyz.clinic.databasev2.room;

import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
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
@RequestMapping("/api/resources")
public class DatabaseV2RoomController {

    private final DatabaseV2RoomRepository repository;
    private final DatabaseV2BranchRepository branchRepository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2RoomController(
            DatabaseV2RoomRepository repository,
            DatabaseV2BranchRepository branchRepository,
            CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.branchRepository = branchRepository;
        this.actorProvider = actorProvider;
    }

    public record Request(
            @NotBlank String branchCode,
            String code,
            @NotBlank String name,
            String type,
            String status,
            Boolean active
    ) {}

    public record Response(
            Long id,
            String branchCode,
            String branchName,
            String code,
            String name,
            String type,
            String status,
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
        DatabaseV2Branch branch = branchRepository.findByCode(request.branchCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา"));
        boolean active = request.active() == null || request.active();
        DatabaseV2RoomRepository.RoomRecord created = repository.create(
                branch.getId(), request.name().trim(), request.type(), active, actorId
        );
        return toResponse(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2RoomRepository.RoomRecord existing = find(id);
        if (!existing.branchCode().equalsIgnoreCase(request.branchCode().trim())) {
            throw new IllegalArgumentException("ไม่อนุญาตให้ย้ายสาขาของทรัพยากร");
        }
        boolean active = request.active() != null ? request.active() : existing.active();
        DatabaseV2RoomRepository.RoomRecord updated = repository.update(
                id, request.name().trim(), request.type(), active, actorId
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

    private DatabaseV2RoomRepository.RoomRecord find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบทรัพยากร"));
    }

    private Response toResponse(DatabaseV2RoomRepository.RoomRecord r) {
        return new Response(
                r.id(),
                r.branchCode(),
                r.branchName(),
                "ROOM-" + r.id(),
                r.roomName(),
                r.roomType(),
                r.active() ? "AVAILABLE" : "MAINTENANCE",
                r.active()
        );
    }
}
