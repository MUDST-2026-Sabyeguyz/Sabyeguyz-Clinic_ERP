package com.sabyeguyz.clinic.databasev2.branch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database-v2")
@RequestMapping("/api/branches")
public class DatabaseV2BranchController {
    private final DatabaseV2BranchRepository branches;

    public DatabaseV2BranchController(DatabaseV2BranchRepository branches) {
        this.branches = branches;
    }

    public record Request(
            @NotBlank @Pattern(regexp = "\\d{2}") String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Size(max = 20) String phone) {}

    public record Response(Long id, String code, String name, String address, String phone, boolean active) {}

    @GetMapping
    List<Response> list() {
        return branches.findAllByOrderByName().stream().map(DatabaseV2BranchController::response).toList();
    }

    @GetMapping("/{id}")
    Response get(@PathVariable Long id) { return response(find(id)); }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    Response create(@Valid @RequestBody Request request, JwtAuthenticationToken authentication) {
        String code = request.code().trim();
        if (branches.findByCode(code).isPresent()) throw new IllegalArgumentException("รหัสสาขาซ้ำ");
        return response(branches.create(code, request.name().trim(), request.address().trim(),
                request.phone().trim(), actorId(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    Response update(@PathVariable Long id, @Valid @RequestBody Request request,
                    JwtAuthenticationToken authentication) {
        DatabaseV2Branch branch = find(id);
        if (!branch.getCode().equals(request.code().trim())) {
            throw new IllegalArgumentException("ไม่อนุญาตให้แก้ branch code เพื่อรักษา HN เดิม");
        }
        return response(branches.update(id, request.name().trim(), request.address().trim(),
                request.phone().trim(), actorId(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable Long id, JwtAuthenticationToken authentication) {
        DatabaseV2Branch branch = find(id);
        branches.deactivate(id, actorId(authentication));
    }

    private DatabaseV2Branch find(Long id) {
        return branches.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา"));
    }

    private static Long actorId(JwtAuthenticationToken authentication) {
        return Long.valueOf(authentication.getToken().getSubject());
    }

    private static Response response(DatabaseV2Branch branch) {
        return new Response(branch.getId(), branch.getCode(), branch.getName(), branch.getAddress(),
                branch.getPhone(), branch.isActive());
    }
}
