package com.sabyeguyz.clinic.therapist;

import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.context.annotation.Profile("!database-v2")
@RestController
@RequestMapping("/api/therapists")
@PreAuthorize("hasRole('MANAGER')")
@Transactional
public class TherapistManagementController {
    private final TherapistRepository therapists;
    private final BranchRepository branches;

    public TherapistManagementController(TherapistRepository therapists, BranchRepository branches) {
        this.therapists = therapists;
        this.branches = branches;
    }

    public record Request(@NotBlank @Size(max = 160) String fullName,
                          String branchCode, @Size(max = 20) String code, Boolean active) {}

    public record Response(Long id, String code, String fullName, String branchCode,
                           String branchName, boolean active) {}

    @GetMapping
    public List<Response> list() {
        return therapists.findAll().stream()
                .map(this::out)
                .sorted(Comparator.comparing(Response::fullName))
                .toList();
    }

    @PostMapping
    public Response create(@Valid @RequestBody Request request) {
        Branch branch = branches.findByCodeAndActiveTrue(normalize(request.branchCode()))
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน"));
        String code = request.code() == null || request.code().isBlank()
                ? nextCode(branch.getCode()) : normalize(request.code());
        if (therapists.findAll().stream().anyMatch(item -> item.getCode().equalsIgnoreCase(code)))
            throw new IllegalArgumentException("รหัสนักกายภาพบำบัดซ้ำ");
        Therapist therapist = new Therapist(branch, code, request.fullName().trim());
        if (Boolean.FALSE.equals(request.active())) therapist.deactivate();
        return out(therapists.save(therapist));
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        Therapist therapist = therapists.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบนักกายภาพบำบัด"));
        therapist.rename(request.fullName().trim());
        if (request.active() != null) {
            if (request.active()) therapist.activate(); else therapist.deactivate();
        }
        return out(therapists.save(therapist));
    }

    private Response out(Therapist therapist) {
        return new Response(therapist.getId(), therapist.getCode(), therapist.getFullName(),
                therapist.getBranch().getCode(), therapist.getBranch().getName(), therapist.isActive());
    }

    private String nextCode(String branchCode) {
        int next = therapists.findAll().stream()
                .filter(item -> item.getCode().startsWith(branchCode + "-T"))
                .map(item -> item.getCode().substring((branchCode + "-T").length()))
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
