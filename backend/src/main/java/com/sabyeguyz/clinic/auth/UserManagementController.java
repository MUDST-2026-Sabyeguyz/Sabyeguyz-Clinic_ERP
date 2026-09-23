package com.sabyeguyz.clinic.auth;

import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import com.sabyeguyz.clinic.staff.Staff;
import com.sabyeguyz.clinic.staff.StaffRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('MANAGER')")
public class UserManagementController {
    private final AppUserRepository users;
    private final StaffRepository staff;
    private final BranchRepository branches;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserManagementController(AppUserRepository users, StaffRepository staff, BranchRepository branches) {
        this.users = users;
        this.staff = staff;
        this.branches = branches;
    }

    public record Request(
            @NotBlank @Size(max = 80) String username,
            String email,
            @Size(min = 8, max = 100) String password,
            @NotNull Role role,
            @NotBlank @Size(max = 40) String employeeCode,
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 40) String phone,
            String branchCode) {}

    public record Response(
            Long id,
            String username,
            String email,
            Role role,
            String employeeCode,
            String fullName,
            String phone,
            String branchCode,
            String branchName,
            boolean active) {}

    @GetMapping
    @Transactional
    public List<Response> list() {
        return staff.findAllByOrderByFullName().stream().map(this::out).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Response create(@Valid @RequestBody Request request) {
        String username = request.username().trim();
        String email = normalize(request.email());
        if (users.findByUsername(username).isPresent()
                || (email != null && users.findByEmail(email).isPresent())) {
            throw new IllegalArgumentException("ชื่อผู้ใช้หรืออีเมลนี้มีอยู่แล้ว");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new IllegalArgumentException("การสร้างผู้ใช้ต้องมีรหัสผ่านอย่างน้อย 8 ตัวอักษร");
        }
        String employeeCode = request.employeeCode().trim();
        if (staff.findByEmployeeCode(employeeCode).isPresent()) {
            throw new IllegalArgumentException("รหัสพนักงานซ้ำ");
        }
        AppUser user = users.save(
                new AppUser(username, email, encoder.encode(request.password()), request.role()));
        Staff value = new Staff(
                user, employeeCode, request.fullName().trim(), normalize(request.phone()));
        value.assignBranch(resolveBranch(request.branchCode()));
        return out(staff.save(value));
    }

    @PutMapping("/{id}")
    @Transactional
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        Staff current = findStaff(id);
        AppUser user = current.getUser();
        String username = request.username().trim();
        String email = normalize(request.email());
        if (users.findByUsername(username).filter(item -> !item.getId().equals(id)).isPresent()
                || (email != null
                        && users.findByEmail(email).filter(item -> !item.getId().equals(id)).isPresent())) {
            throw new IllegalArgumentException("ชื่อผู้ใช้หรืออีเมลนี้มีอยู่แล้ว");
        }
        staff.findByEmployeeCode(request.employeeCode().trim())
                .filter(item -> !item.getId().equals(id))
                .ifPresent(item -> {
                    throw new IllegalArgumentException("รหัสพนักงานซ้ำ");
                });
        user.update(username, email, request.role());
        if (request.password() != null && !request.password().isBlank()) {
            user.updatePassword(encoder.encode(request.password()));
        }
        current.update(request.employeeCode().trim(), request.fullName().trim(), normalize(request.phone()));
        current.assignBranch(resolveBranch(request.branchCode()));
        return out(staff.save(current));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        Staff current = findStaff(id);
        current.deactivate();
        current.getUser().deactivate();
        staff.save(current);
    }

    @PatchMapping("/{id}/activate")
    @Transactional
    public Response activate(@PathVariable Long id) {
        Staff current = findStaff(id);
        current.getUser().activate();
        current.activate();
        return out(staff.save(current));
    }

    private Staff findStaff(Long id) {
        return staff.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้ Staff"));
    }

    private Response out(Staff value) {
        AppUser user = value.getUser();
        Branch branch = value.getBranch();
        return new Response(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                value.getEmployeeCode(),
                value.getFullName(),
                value.getPhone(),
                branch == null ? null : branch.getCode(),
                branch == null ? null : branch.getName(),
                user.isActive() && value.isActive());
    }

    private Branch resolveBranch(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return branches.findByCodeAndActiveTrue(code.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน " + code));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
