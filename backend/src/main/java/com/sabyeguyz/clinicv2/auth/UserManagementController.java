package com.sabyeguyz.clinicv2.auth;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.enums.StaffPosition;
import com.sabyeguyz.clinicv2.role.Role;
import com.sabyeguyz.clinicv2.role.RoleRepository;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's userManagementApi (GET/POST/PUT
 * /api/users, DELETE/PATCH .../activate). Old schema shared one row (staff.id
 * = app_users.id) for "the user account" and "the staff member"; v2 splits
 * these into separate entities (App_user, Staff) joined by a nullable FK.
 * This controller re-creates the old combined create/update flow by driving
 * both StaffService and AppUserService together, so it stays a drop-in
 * replacement for the frontend even though the schema underneath changed.
 *
 * <p>Known gaps vs. the old API, both flagged for the team:</p>
 * <ul>
 *   <li>{@code fullName} is split into Staff's separate fname/lname by the
 *   first space — wrong for a name with no space or multiple given names.
 *   The frontend form should really collect these separately.</li>
 *   <li>v2's Staff.branch is NOT NULL, so branchCode is now required here
 *   even though the old API treated it as optional.</li>
 *   <li>{@code role} doubles as both the assigned Role name (User_role) and
 *   the Staff.position (Therapist/Manager) — same conflation the old system
 *   had, just made explicit.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
public class UserManagementController {
    private final AppUserRepository users;
    private final AppUserService appUserService;
    private final StaffService staffService;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final BranchRepository branches;
    private final CurrentActor actor;

    public UserManagementController(AppUserRepository users, AppUserService appUserService,
                                     StaffService staffService, RoleRepository roles, UserRoleRepository userRoles,
                                     BranchRepository branches, CurrentActor actor) {
        this.users = users;
        this.appUserService = appUserService;
        this.staffService = staffService;
        this.roles = roles;
        this.userRoles = userRoles;
        this.branches = branches;
        this.actor = actor;
    }

    public record Request(
            @NotBlank @Size(max = 50) String username,
            String email,
            @Size(min = 8, max = 100) String password,
            @NotNull String role,
            @NotBlank @Size(max = 20) String employeeCode,
            @NotBlank @Size(max = 160) String fullName,
            String phone,
            @NotBlank String branchCode) {}

    public record Response(Long id, String username, String email, String role, String employeeCode,
                            String fullName, String phone, String branchCode, String branchName, boolean active) {}

    @GetMapping
    public List<Response> list() {
        return users.findAll().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request request) {
        Branch branch = resolveBranch(request.branchCode());
        String[] names = splitName(request.fullName());
        StaffPosition position = resolvePosition(request.role());

        Staff staff = staffService.create(request.employeeCode(), names[0], names[1], position, branch,
                LocalDate.now(), actor.currentId());
        AppUser user = appUserService.register(request.username(), request.password(), staff, actor.currentId());
        Role role = resolveOrCreateRole(request.role());
        appUserService.assignRole(user.getId(), role.getId());
        appUserService.grantBranchAccess(user.getId(), branch.getId());

        return toResponse(users.findById(user.getId()).orElseThrow());
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        AppUser user = findUser(id);
        Staff staff = user.getStaff();
        if (staff == null) {
            throw new IllegalArgumentException("ผู้ใช้นี้ไม่ได้ผูกกับข้อมูลพนักงาน");
        }
        Branch branch = resolveBranch(request.branchCode());
        String[] names = splitName(request.fullName());
        StaffPosition position = resolvePosition(request.role());
        staffService.update(staff.getId(), names[0], names[1], position, branch, actor.currentId());

        appUserService.updateProfile(id, request.username(), request.password(), actor.currentId());
        userRoles.deleteByUserId(id);
        Role role = resolveOrCreateRole(request.role());
        appUserService.assignRole(id, role.getId());
        appUserService.grantBranchAccess(id, branch.getId());

        return toResponse(users.findById(id).orElseThrow());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        appUserService.deactivate(id);
    }

    @PatchMapping("/{id}/activate")
    public Response activate(@PathVariable Long id) {
        appUserService.activate(id);
        return toResponse(findUser(id));
    }

    private AppUser findUser(Long id) {
        return users.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
    }

    private Branch resolveBranch(String branchCode) {
        return branches.findByBranchCode(branchCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + branchCode));
    }

    private Role resolveOrCreateRole(String roleName) {
        String name = roleName.trim().toUpperCase();
        return roles.findByRoleName(name).orElseGet(() -> {
            Role role = new Role();
            role.setRoleName(name);
            role.setRoleCreateAt(Instant.now());
            role.setRoleCreateBy(actor.currentId());
            return roles.save(role);
        });
    }

    private static StaffPosition resolvePosition(String role) {
        return "MANAGER".equalsIgnoreCase(role) ? StaffPosition.Manager : StaffPosition.Therapist;
    }

    private static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? new String[] {trimmed, "-"} : new String[] {trimmed.substring(0, space), trimmed.substring(space + 1)};
    }

    private Response toResponse(AppUser user) {
        Staff staff = user.getStaff();
        String roleName = userRoles.findByUserId(user.getId()).stream()
                .map(userRole -> userRole.getRole().getRoleName())
                .findFirst().orElse(null);
        Branch branch = staff == null ? null : staff.getBranch();
        // Staff has no phone column in the v2 data dictionary (a gap vs. the old API) - always null here.
        return new Response(user.getId(), user.getUsername(), null, roleName,
                staff == null ? null : staff.getStaffCode(),
                staff == null ? null : staff.getStaffFname() + " " + staff.getStaffLname(),
                null,
                branch == null ? null : branch.getBranchCode(),
                branch == null ? null : branch.getBranchName(),
                user.isUserStatus());
    }
}
