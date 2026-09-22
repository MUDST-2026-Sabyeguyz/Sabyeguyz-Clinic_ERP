package com.sabyeguyz.clinic.databasev2.staff;

import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api/therapists")
@PreAuthorize("hasRole('MANAGER')")
public class DatabaseV2TherapistController {

    private final DatabaseV2StaffRepository staffRepository;
    private final DatabaseV2BranchRepository branchRepository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2TherapistController(
            DatabaseV2StaffRepository staffRepository,
            DatabaseV2BranchRepository branchRepository,
            CurrentActorProvider actorProvider) {
        this.staffRepository = staffRepository;
        this.branchRepository = branchRepository;
        this.actorProvider = actorProvider;
    }

    public record Request(
            @NotBlank @Size(max = 160) String fullName,
            String branchCode,
            @Size(max = 20) String code,
            Boolean active
    ) {}

    public record Response(
            Long id,
            String code,
            String fullName,
            String branchCode,
            String branchName,
            boolean active
    ) {}

    @GetMapping
    public List<Response> list() {
        return staffRepository.findByPosition("Therapist").stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(Response::fullName))
                .toList();
    }

    @PostMapping
    public Response create(@Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2Branch branch = branchRepository.findByCode(normalize(request.branchCode()))
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน"));

        String code = request.code() == null || request.code().isBlank()
                ? nextCode(branch.getCode()) : request.code().trim().toUpperCase();

        if (staffRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("รหัสนักกายภาพบำบัดซ้ำ");
        }

        String[] names = DatabaseV2StaffController.splitFullName(request.fullName());
        DatabaseV2StaffRepository.StaffModel created = staffRepository.create(
                code, names[0], names[1], "Therapist", branch.getId(), LocalDate.now(), actorId
        );
        if (Boolean.FALSE.equals(request.active())) {
            staffRepository.setStatus(created.id(), false, actorId);
            created = staffRepository.findById(created.id()).orElseThrow();
        }
        return toResponse(created);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2StaffRepository.StaffModel staff = staffRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบนักกายภาพบำบัด"));

        String[] names = DatabaseV2StaffController.splitFullName(request.fullName());
        DatabaseV2StaffRepository.StaffModel updated = staffRepository.update(
                id, staff.code(), names[0], names[1], staff.position(), staff.branchId(), actorId
        );
        if (request.active() != null) {
            staffRepository.setStatus(id, request.active(), actorId);
            updated = staffRepository.findById(id).orElseThrow();
        }
        return toResponse(updated);
    }

    private Response toResponse(DatabaseV2StaffRepository.StaffModel s) {
        return new Response(
                s.id(),
                s.code(),
                s.fullName(),
                s.branchCode(),
                s.branchName(),
                s.active()
        );
    }

    private String nextCode(String branchCode) {
        int next = staffRepository.findByPosition("Therapist").stream()
                .filter(item -> item.code() != null && item.code().startsWith(branchCode + "-T"))
                .map(item -> item.code().substring((branchCode + "-T").length()))
                .filter(value -> value.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
        return branchCode + "-T" + String.format("%02d", next);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
