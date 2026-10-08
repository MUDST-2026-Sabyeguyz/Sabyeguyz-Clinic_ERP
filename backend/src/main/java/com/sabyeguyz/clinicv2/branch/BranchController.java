package com.sabyeguyz.clinicv2.branch;

import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's branchApi (GET/POST/PUT/DELETE
 * /api/branches). Response shape is intentionally NOT backward compatible
 * with the old {@code {id, code, numericCode, name, active}} — v2's Branch
 * data dictionary merges code+numericCode into one branchCode and adds
 * address/phone. The frontend's Branch type and branchApi calls need
 * updating to match this shape.
 */
@Transactional
@RestController
@RequestMapping("/api/branches")
public class BranchController {
    private final BranchService service;
    private final CurrentActor actor;

    public BranchController(BranchService service, CurrentActor actor) {
        this.service = service;
        this.actor = actor;
    }

    public record Request(
            @NotBlank @Pattern(regexp = "\\d{2}", message = "branchCode ต้องเป็นตัวเลข 2 หลัก") String branchCode,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Size(max = 20) String phone) {}

    public record Response(Long id, String branchCode, String name, String address, String phone, boolean active) {}

    @GetMapping
    public List<Response> list() {
        return service.list().stream().map(BranchController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return toResponse(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request request) {
        Branch branch = service.create(request.branchCode(), request.name(), request.address(), request.phone(),
                actor.currentId());
        return toResponse(branch);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        Branch existing = service.get(id);
        if (!existing.getBranchCode().equals(request.branchCode().trim())) {
            throw new IllegalArgumentException("ไม่อนุญาตให้แก้ branchCode เพื่อรักษา HN เดิม");
        }
        Branch branch = service.update(id, request.name(), request.address(), request.phone(), actor.currentId());
        return toResponse(branch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id, actor.currentId());
    }

    private static Response toResponse(Branch branch) {
        return new Response(branch.getId(), branch.getBranchCode(), branch.getBranchName(),
                branch.getAddress(), branch.getPhone(), branch.isBranchStatus());
    }
}
