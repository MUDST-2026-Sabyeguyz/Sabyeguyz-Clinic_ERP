package com.sabyeguyz.clinic.branch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/branches")
public class BranchController {
    private final BranchRepository repository;

    public BranchController(BranchRepository repository) { this.repository = repository; }

    public record Request(@NotBlank @Size(max = 2) String code,
                          @NotBlank @Pattern(regexp = "\\d{2}", message = "numericCode ต้องเป็นตัวเลข 2 หลัก") String numericCode,
                          @NotBlank @Size(max = 120) String name,
                          Boolean active) {}
    public record Response(Long id, String code, String numericCode, String name, boolean active) {}

    @GetMapping
    List<Response> list() { return repository.findAllByOrderByName().stream().map(BranchController::toResponse).toList(); }

    @GetMapping("/{id}")
    Response get(@PathVariable Long id) { return toResponse(find(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Response create(@Valid @RequestBody Request request) {
        String code = normalize(request.code());
        String numericCode = request.numericCode().trim();
        if (repository.findByCode(code).isPresent() || repository.findByNumericCode(numericCode).isPresent())
            throw new IllegalArgumentException("รหัสสาขาซ้ำ");
        Branch branch = new Branch(code, numericCode, request.name().trim());
        if (request.active() != null) {
            if (request.active()) branch.activate(); else branch.deactivate();
        }
        return toResponse(repository.save(branch));
    }

    @PutMapping("/{id}")
    Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        Branch branch = find(id);
        String code = normalize(request.code());
        String numericCode = request.numericCode().trim();
        if (!numericCode.equals(branch.getNumericCode()))
            throw new IllegalArgumentException("ไม่อนุญาตให้แก้ numericCode เพื่อรักษา HN เดิม");
        if (repository.findByCode(code).filter(found -> !found.getId().equals(id)).isPresent()
                || repository.findByNumericCode(numericCode).filter(found -> !found.getId().equals(id)).isPresent())
            throw new IllegalArgumentException("รหัสสาขาซ้ำ");
        branch.update(code, numericCode, request.name().trim());
        if (request.active() != null) {
            if (request.active()) branch.activate(); else branch.deactivate();
        }
        return toResponse(repository.save(branch));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable Long id) { Branch branch = find(id); branch.deactivate(); repository.save(branch); }

    private Branch find(Long id) { return repository.findById(id).orElseThrow(BranchNotFoundException::new); }
    private static String normalize(String value) { return value.trim().toUpperCase(); }
    private static Response toResponse(Branch branch) { return new Response(branch.getId(), branch.getCode(), branch.getNumericCode(), branch.getName(), branch.isActive()); }
}
