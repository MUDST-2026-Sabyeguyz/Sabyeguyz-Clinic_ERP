package com.sabyeguyz.clinicv2.staff;

import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.enums.StaffPosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's staffApi (GET/POST/PUT/DELETE
 * /api/staff) — only GET is actually called from the UI today (services
 * view, to list therapists). Response has no userId/username/role: those
 * belong to App_user in v2 (a separate entity, not 1:1 with Staff anymore),
 * so this only exposes Staff's own columns. Prefer {@code /api/users} for
 * anything that needs the account fields together with Staff.
 */
@Transactional
@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private final StaffService service;
    private final BranchRepository branches;
    private final CurrentActor actor;

    public StaffController(StaffService service, BranchRepository branches, CurrentActor actor) {
        this.service = service;
        this.branches = branches;
        this.actor = actor;
    }

    public record Request(@NotBlank String employeeCode, @NotBlank String fullName, String phone,
                           @NotBlank String branchCode, String position) {}

    public record Response(Long id, String employeeCode, String fullName, String position,
                            String branchCode, String branchName, boolean active) {}

    @GetMapping
    public List<Response> list() {
        return service.list().stream().map(StaffController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return toResponse(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request request) {
        Branch branch = resolveBranch(request.branchCode());
        String[] names = splitName(request.fullName());
        StaffPosition position = resolvePosition(request.position());
        Staff staff = service.create(request.employeeCode(), names[0], names[1], position, branch,
                LocalDate.now(), actor.currentId());
        return toResponse(staff);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        Branch branch = resolveBranch(request.branchCode());
        String[] names = splitName(request.fullName());
        StaffPosition position = resolvePosition(request.position());
        Staff staff = service.update(id, names[0], names[1], position, branch, actor.currentId());
        return toResponse(staff);
    }

    private Branch resolveBranch(String branchCode) {
        return branches.findByBranchCode(branchCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + branchCode));
    }

    private static StaffPosition resolvePosition(String position) {
        return "Manager".equalsIgnoreCase(position) ? StaffPosition.Manager : StaffPosition.Therapist;
    }

    private static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? new String[] {trimmed, "-"} : new String[] {trimmed.substring(0, space), trimmed.substring(space + 1)};
    }

    private static Response toResponse(Staff staff) {
        Branch branch = staff.getBranch();
        return new Response(staff.getId(), staff.getStaffCode(),
                staff.getStaffFname() + " " + staff.getStaffLname(), staff.getPosition().name(),
                branch == null ? null : branch.getBranchCode(), branch == null ? null : branch.getBranchName(),
                staff.isStaffStatus());
    }
}
