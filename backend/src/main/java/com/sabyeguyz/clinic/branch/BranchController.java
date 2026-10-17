package com.sabyeguyz.clinic.branch;

import com.sabyeguyz.clinic.audit.TransactionActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/branches")
public class BranchController {
    private final BranchRepository repository;
    private final TransactionActor actor;

    public BranchController(BranchRepository repository, TransactionActor actor) {
        this.repository = repository;
        this.actor = actor;
    }

    public record Request(
            @NotBlank @Pattern(regexp = "\\d{2}", message = "branchCode ต้องเป็นตัวเลข 2 หลัก") String branchCode,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Size(max = 20) String phone) {}

    public record Response(
            Long id, String branchCode, String name, String address, String phone, boolean active,
            Instant createdAt, Long createdBy, Instant updatedAt, Long updatedBy) {}

    @GetMapping
    List<Response> list() { return repository.findAllByOrderByName().stream().map(BranchController::toResponse).toList(); }

    @GetMapping("/{id}")
    Response get(@PathVariable Long id) { return toResponse(find(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Response create(@Valid @RequestBody Request request) {
        String branchCode = request.branchCode().trim();
        if (repository.findByBranchCode(branchCode).isPresent())
            throw new IllegalArgumentException("รหัสสาขาซ้ำ");
        Branch branch = new Branch(branchCode, request.name().trim(), request.address().trim(),
                request.phone().trim(), actor.currentId());
        return toResponse(repository.save(branch));
    }

    @PutMapping("/{id}")
    Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        Branch branch = find(id);
        // branchCode is not editable to preserve the HN prefix already issued for this branch.
        if (!branch.getBranchCode().equals(request.branchCode().trim()))
            throw new IllegalArgumentException("ไม่อนุญาตให้แก้ branchCode เพื่อรักษา HN เดิม");
        branch.update(request.name().trim(), request.address().trim(), request.phone().trim(), actor.currentId());
        return toResponse(repository.save(branch));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable Long id) {
        Branch branch = find(id);
        branch.deactivate(actor.currentId());
        repository.save(branch);
    }

    private Branch find(Long id) { return repository.findById(id).orElseThrow(BranchNotFoundException::new); }

    private static Response toResponse(Branch branch) {
        return new Response(branch.getId(), branch.getBranchCode(), branch.getName(), branch.getAddress(),
                branch.getPhone(), branch.isActive(), branch.getCreatedAt(), branch.getCreatedBy(),
                branch.getUpdatedAt(), branch.getUpdatedBy());
    }
}
