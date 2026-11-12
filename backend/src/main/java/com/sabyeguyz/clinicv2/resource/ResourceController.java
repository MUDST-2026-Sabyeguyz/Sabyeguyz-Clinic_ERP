package com.sabyeguyz.clinicv2.resource;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;

/**
 * Matches frontend/src/api/client.ts's resourceApi (/api/resources). v2's
 * Room has no "code" column (just room_name/room_type) - code is always
 * null here, a real gap vs. the old ClinicResource type.
 */
@Transactional
@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final RoomRepository repository;
    private final BranchRepository branches;
    private final CurrentActor actor;

    public ResourceController(
            RoomRepository repository,
            BranchRepository branches,
            CurrentActor actor) {
        this.repository = repository;
        this.branches = branches;
        this.actor = actor;
    }

    public record Request(
            @NotBlank String branchCode,
            String code,
            @NotBlank String name,
            String type,
            String status) {}

    public record Response(
            Long id,
            String branchCode,
            String branchName,
            String code,
            String name,
            String type,
            String status,
            boolean active) {}

    @GetMapping
    public List<Response> list() {
        return repository.findAll()
                .stream()
                .map(ResourceController::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public Response create(@RequestBody Request request) {
        Branch branch = resolveBranch(request.branchCode());

        Room room = new Room();
        room.setBranch(branch);
        room.setRoomName(request.name());
        room.setRoomType(request.type());
        room.setRoomStatus(true);
        room.setRoomCreateAt(Instant.now());
        room.setRoomCreateBy(actor.currentId());

        return toResponse(repository.save(room));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public Response update(
            @PathVariable Long id,
            @RequestBody Request request) {
        Room room = find(id);

        room.setRoomName(request.name());
        room.setRoomType(request.type());
        room.setRoomUpdateAt(Instant.now());
        room.setRoomUpdateBy(actor.currentId());

        return toResponse(repository.save(room));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public void deactivate(@PathVariable Long id) {
        Room room = find(id);

        room.setRoomStatus(false);

        repository.save(room);
    }

    @PatchMapping("/{id}/activate")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('MANAGER')")
    public Response activate(@PathVariable Long id) {
        Room room = find(id);

        room.setRoomStatus(true);

        return toResponse(repository.save(room));
    }

    private Room find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบห้อง/ทรัพยากร"));
    }

    private Branch resolveBranch(String branchCode) {
        return branches.findByBranchCode(branchCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + branchCode));
    }

    private static Response toResponse(Room room) {
        Branch branch = room.getBranch();

        return new Response(
                room.getId(),
                branch.getBranchCode(),
                branch.getBranchName(),
                null,
                room.getRoomName(),
                room.getRoomType(),
                room.isRoomStatus() ? "ACTIVE" : "INACTIVE",
                room.isRoomStatus());
    }
}
