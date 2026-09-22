package com.sabyeguyz.clinic.databasev2.user;

import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import com.sabyeguyz.clinic.databasev2.security.DatabaseV2SecurityRepository;
import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffController;
import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api/users")
@PreAuthorize("hasRole('MANAGER')")
public class DatabaseV2UserController {

    public record Request(
            @NotBlank @Size(max = 50) String username,
            String email,
            @Size(min = 8, max = 100) String password,
            @NotNull String role,
            @NotBlank @Size(max = 20) String employeeCode,
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 40) String phone,
            String branchCode,
            Boolean active
    ) {}

    public record Response(
            Long id,
            String username,
            String email,
            String role,
            String employeeCode,
            String fullName,
            String phone,
            String branchCode,
            String branchName,
            boolean active
    ) {}

    private final JdbcTemplate jdbc;
    private final DatabaseV2StaffRepository staffRepository;
    private final DatabaseV2BranchRepository branchRepository;
    private final DatabaseV2SecurityRepository securityRepository;
    private final CurrentActorProvider actorProvider;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DatabaseV2UserController(
            JdbcTemplate jdbc,
            DatabaseV2StaffRepository staffRepository,
            DatabaseV2BranchRepository branchRepository,
            DatabaseV2SecurityRepository securityRepository,
            CurrentActorProvider actorProvider) {
        this.jdbc = jdbc;
        this.staffRepository = staffRepository;
        this.branchRepository = branchRepository;
        this.securityRepository = securityRepository;
        this.actorProvider = actorProvider;
    }

    private static final String SELECT_USER_ROWS = """
            select u.user_id, u.username, u.user_status, u.staff_id,
                   s.staff_code, s.staff_fname, s.staff_lname, s.staff_status,
                   b.branch_code, b.branch_name,
                   (select r.role_name from role r join user_role ur on ur.role_id = r.role_id where ur.user_id = u.user_id limit 1) as role_name
            from app_user u
            left join staff s on s.staff_id = u.staff_id
            left join branch b on b.branch_id = s.branch_id
            order by s.staff_fname, s.staff_lname, u.username
            """;

    @GetMapping
    public List<Response> list() {
        return jdbc.query(SELECT_USER_ROWS, (rs, rowNum) -> {
            String fname = rs.getString("staff_fname");
            String lname = rs.getString("staff_lname");
            String fullName = (fname != null ? fname : "") + (lname != null && !"-".equals(lname) ? " " + lname : "");
            return new Response(
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    null,
                    rs.getString("role_name"),
                    rs.getString("staff_code"),
                    fullName.trim().isEmpty() ? rs.getString("username") : fullName.trim(),
                    null,
                    rs.getString("branch_code"),
                    rs.getString("branch_name"),
                    rs.getBoolean("user_status") && (rs.getObject("staff_status") == null || rs.getBoolean("staff_status"))
            );
        });
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Response create(@Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        String username = request.username().trim();
        Integer existingUserCount = jdbc.queryForObject(
                "select count(*) from app_user where lower(username) = lower(?)", Integer.class, username);
        if (existingUserCount != null && existingUserCount > 0) {
            throw new IllegalArgumentException("ชื่อผู้ใช้นี้มีอยู่แล้ว");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new IllegalArgumentException("การสร้างผู้ใช้ต้องมีรหัสผ่านอย่างน้อย 8 ตัวอักษร");
        }
        String employeeCode = request.employeeCode().trim();
        if (staffRepository.findByCode(employeeCode).isPresent()) {
            throw new IllegalArgumentException("รหัสพนักงานซ้ำ");
        }

        DatabaseV2Branch branch = resolveBranch(request.branchCode());
        String roleName = request.role().trim().toUpperCase();
        String position = roleName.contains("MANAGER") ? "Manager" : "Therapist";
        String[] names = DatabaseV2StaffController.splitFullName(request.fullName());

        DatabaseV2StaffRepository.StaffModel staff = staffRepository.create(
                employeeCode, names[0], names[1], position, branch.getId(), LocalDate.now(), actorId
        );

        String passwordHash = encoder.encode(request.password());
        Long userId = jdbc.queryForObject(
                """
                insert into app_user (
                    username, password_hash, staff_id, user_status, last_login_at,
                    user_create_at, user_create_by
                ) values (
                    ?, ?, ?, ?, ?, ?, ?
                ) returning user_id
                """,
                Long.class,
                username, passwordHash, staff.id(),
                request.active() == null || request.active(),
                LocalDateTime.now(), LocalDateTime.now(), actorId
        );

        DatabaseV2SecurityRepository.RoleRecord role = securityRepository.getOrCreateRole(roleName, actorId);
        securityRepository.assignRoleToUser(userId, role.id());
        securityRepository.assignBranchAccess(userId, branch.getId());

        if (Boolean.FALSE.equals(request.active())) {
            staffRepository.setStatus(staff.id(), false, actorId);
        }

        return new Response(
                userId, username, request.email(), roleName,
                employeeCode, staff.fullName(), request.phone(),
                branch.getCode(), branch.getName(),
                request.active() == null || request.active()
        );
    }

    @PutMapping("/{id}")
    @Transactional
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        Long staffId = jdbc.query(
                "select staff_id from app_user where user_id = ?",
                (rs, rowNum) -> (Long) rs.getObject("staff_id"),
                id
        ).stream().findFirst().orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ใช้"));

        String username = request.username().trim();
        Integer existingOther = jdbc.queryForObject(
                "select count(*) from app_user where lower(username) = lower(?) and user_id <> ?",
                Integer.class, username, id);
        if (existingOther != null && existingOther > 0) {
            throw new IllegalArgumentException("ชื่อผู้ใช้นี้มีอยู่แล้ว");
        }

        DatabaseV2Branch branch = resolveBranch(request.branchCode());
        String roleName = request.role().trim().toUpperCase();
        String position = roleName.contains("MANAGER") ? "Manager" : "Therapist";
        String[] names = DatabaseV2StaffController.splitFullName(request.fullName());

        if (staffId != null) {
            staffRepository.findByCode(request.employeeCode().trim())
                    .filter(s -> !s.id().equals(staffId))
                    .ifPresent(s -> { throw new IllegalArgumentException("รหัสพนักงานซ้ำ"); });
            staffRepository.update(staffId, request.employeeCode().trim(), names[0], names[1], position, branch.getId(), actorId);
            if (request.active() != null) {
                staffRepository.setStatus(staffId, request.active(), actorId);
            }
        }

        if (request.password() != null && !request.password().isBlank()) {
            jdbc.update("update app_user set username = ?, password_hash = ?, user_status = ?, user_update_at = ?, user_update_by = ? where user_id = ?",
                    username, encoder.encode(request.password()), request.active() != null ? request.active() : true, LocalDateTime.now(), actorId, id);
        } else {
            jdbc.update("update app_user set username = ?, user_status = ?, user_update_at = ?, user_update_by = ? where user_id = ?",
                    username, request.active() != null ? request.active() : true, LocalDateTime.now(), actorId, id);
        }

        DatabaseV2SecurityRepository.RoleRecord role = securityRepository.getOrCreateRole(roleName, actorId);
        securityRepository.removeRolesFromUser(id);
        securityRepository.assignRoleToUser(id, role.id());
        securityRepository.assignBranchAccess(id, branch.getId());

        return new Response(
                id, username, request.email(), roleName,
                request.employeeCode().trim(), request.fullName().trim(), request.phone(),
                branch.getCode(), branch.getName(),
                request.active() == null || request.active()
        );
    }

    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        Long staffId = jdbc.query(
                "select staff_id from app_user where user_id = ?",
                (rs, rowNum) -> (Long) rs.getObject("staff_id"),
                id
        ).stream().findFirst().orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ใช้"));

        jdbc.update("update app_user set user_status = false, user_update_at = ?, user_update_by = ? where user_id = ?",
                LocalDateTime.now(), actorId, id);
        if (staffId != null) {
            staffRepository.setStatus(staffId, false, actorId);
        }
    }

    @PatchMapping("/{id}/activate")
    @Transactional
    public Response activate(@PathVariable Long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        Long staffId = jdbc.query(
                "select staff_id from app_user where user_id = ?",
                (rs, rowNum) -> (Long) rs.getObject("staff_id"),
                id
        ).stream().findFirst().orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ใช้"));

        jdbc.update("update app_user set user_status = true, user_update_at = ?, user_update_by = ? where user_id = ?",
                LocalDateTime.now(), actorId, id);
        if (staffId != null) {
            staffRepository.setStatus(staffId, true, actorId);
        }
        return list().stream().filter(u -> u.id().equals(id)).findFirst().orElseThrow();
    }

    private DatabaseV2Branch resolveBranch(String code) {
        if (code == null || code.isBlank()) {
            return branchRepository.findAllByOrderByName().stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("ไม่พบข้อมูลสาขาในระบบ"));
        }
        return branchRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + code));
    }
}
