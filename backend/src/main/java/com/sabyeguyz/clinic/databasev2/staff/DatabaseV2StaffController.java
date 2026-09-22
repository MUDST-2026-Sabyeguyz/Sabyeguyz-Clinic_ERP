package com.sabyeguyz.clinic.databasev2.staff;

import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api/staff")
public class DatabaseV2StaffController {
    private final DatabaseV2StaffRepository staffRepository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2StaffController(DatabaseV2StaffRepository staffRepository, CurrentActorProvider actorProvider) {
        this.staffRepository = staffRepository;
        this.actorProvider = actorProvider;
    }

    public record Request(
            Long userId,
            @NotBlank @Size(max = 20) String employeeCode,
            @NotBlank @Size(max = 160) String fullName,
            String phone,
            Long branchId,
            String position
    ) {}

    public record Response(
            Long id,
            Long userId,
            String username,
            String role,
            String employeeCode,
            String fullName,
            String phone,
            boolean active
    ) {}

    @GetMapping
    public List<Response> list() {
        return staffRepository.findAll().stream().map(DatabaseV2StaffController::toResponse).toList();
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
        String code = request.employeeCode().trim();
        if (staffRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("รหัสพนักงานซ้ำ");
        }
        String[] names = splitFullName(request.fullName());
        String position = request.position() != null && !request.position().isBlank()
                ? request.position().trim() : "Therapist";
        Long branchId = request.branchId() != null ? request.branchId() : 1L;

        DatabaseV2StaffRepository.StaffModel created = staffRepository.create(
                code, names[0], names[1], position, branchId, LocalDate.now(), actorId
        );
        return toResponse(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2StaffRepository.StaffModel existing = find(id);
        String code = request.employeeCode().trim();
        staffRepository.findByCode(code).filter(s -> !s.id().equals(id)).ifPresent(s -> {
            throw new IllegalArgumentException("รหัสพนักงานซ้ำ");
        });
        String[] names = splitFullName(request.fullName());
        String position = request.position() != null && !request.position().isBlank()
                ? request.position().trim() : existing.position();
        Long branchId = request.branchId() != null ? request.branchId() : existing.branchId();

        DatabaseV2StaffRepository.StaffModel updated = staffRepository.update(
                id, code, names[0], names[1], position, branchId, actorId
        );
        return toResponse(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        find(id);
        staffRepository.setStatus(id, false, actorId);
    }

    private DatabaseV2StaffRepository.StaffModel find(Long id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบข้อมูลพนักงาน"));
    }

    public static String[] splitFullName(String fullName) {
        String trimmed = fullName != null ? fullName.trim() : "";
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx > 0) {
            return new String[]{trimmed.substring(0, spaceIdx).trim(), trimmed.substring(spaceIdx + 1).trim()};
        }
        return new String[]{trimmed.isEmpty() ? "-" : trimmed, "-"};
    }

    private static Response toResponse(DatabaseV2StaffRepository.StaffModel s) {
        return new Response(
                s.id(),
                s.userId(),
                s.username(),
                s.role(),
                s.code(),
                s.fullName(),
                null,
                s.active()
        );
    }
}
