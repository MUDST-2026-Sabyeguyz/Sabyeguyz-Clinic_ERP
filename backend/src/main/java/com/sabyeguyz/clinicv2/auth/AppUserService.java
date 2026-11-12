package com.sabyeguyz.clinicv2.auth;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.role.Role;
import com.sabyeguyz.clinicv2.role.RoleRepository;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {
    public static final String INVALID_CREDENTIALS = "ชื่อผู้ใช้ อีเมล หรือรหัสผ่านไม่ถูกต้อง";
    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@.\\s]+(\\.[^@.\\s]+)*@[^@.\\s]+(\\.[^@.\\s]+)+$");
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final BranchRepository branches;
    private final UserBranchAccessRepository userBranchAccess;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AppUserService(AppUserRepository users, RoleRepository roles, UserRoleRepository userRoles,
                           BranchRepository branches, UserBranchAccessRepository userBranchAccess) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.branches = branches;
        this.userBranchAccess = userBranchAccess;
    }

    @Transactional
    public AppUser register(String username, String email, String rawPassword, Staff staff, Long actorId) {
        if (staff != null && staff.getTerminationDate() != null) {
            throw new IllegalArgumentException("ไม่สามารถสร้างบัญชีให้พนักงานที่มีวันลาออกแล้ว");
        }
        String normalized = requireText(username, "username");
        if (normalized.contains("@")) throw new IllegalArgumentException("ชื่อผู้ใช้ห้ามมี @");
        if (users.findByUsername(normalized).isPresent()) {
            throw new IllegalArgumentException("ชื่อผู้ใช้นี้มีอยู่แล้ว");
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("อีเมลนี้มีอยู่แล้ว");
        }
        if (rawPassword == null || rawPassword.length() < 12 || rawPassword.length() > 100) {
            throw new IllegalArgumentException("รหัสผ่านต้องมีความยาว 12-100 ตัวอักษร");
        }
        AppUser user = new AppUser();
        user.setUsername(normalized);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStaff(staff);
        user.setUserStatus(true);
        user.setUserCreateAt(Instant.now());
        user.setUserCreateBy(actorId);
        // last_login_at is NOT NULL in the schema; a new account counts as "seen" at creation.
        user.setLastLoginAt(user.getUserCreateAt());
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public AppUser login(String username, String rawPassword) {
        String login = requireText(username, "username");
        AppUser user = (login.contains("@") ? users.findByEmailIgnoreCase(login) : users.findByUsername(login))
                .orElseThrow(() -> new IllegalArgumentException(INVALID_CREDENTIALS));
        if (!user.isUserStatus() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }
        // staff of a closed branch cannot work at all until it is opened again; a Manager still gets in to open it
        com.sabyeguyz.clinicv2.branch.Branch home = user.getStaff() == null ? null : user.getStaff().getBranch();
        boolean manager = userRoles.findByUserId(user.getId()).stream()
                .anyMatch(role -> "Manager".equalsIgnoreCase(role.getRole().getRoleName()));
        if (home != null && !home.isBranchStatus() && !manager) {
            throw new IllegalArgumentException("สาขา " + home.getBranchName() + " ปิดใช้งาน ไม่สามารถเข้าสู่ระบบได้");
        }
        return user;
    }

    /** A user changes their own password; every token issued before is invalidated through authVersion. */
    @Transactional
    public AppUser changeOwnPassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("รหัสผ่านปัจจุบันไม่ถูกต้อง");
        }
        if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 100) {
            throw new IllegalArgumentException("รหัสผ่านใหม่ต้องมีความยาว 12-100 ตัวอักษร");
        }
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("รหัสผ่านใหม่ต้องไม่ซ้ำกับรหัสผ่านเดิม");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setAuthVersion(user.getAuthVersion() + 1);
        user.setUserUpdateAt(Instant.now());
        user.setUserUpdateBy(userId);
        return users.save(user);
    }

    @Transactional
    public void recordLogin(Long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
        user.setLastLoginAt(Instant.now());
        users.save(user);
    }

    /** Idempotent: assigning a Role the user already has is a no-op. */
    @Transactional
    public void assignRole(Long userId, Long roleId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
        Role role = roles.findById(roleId).orElseThrow(() -> new IllegalArgumentException("ไม่พบบทบาท"));
        UserRoleId id = new UserRoleId(userId, roleId);
        Optional<UserRole> existing = userRoles.findById(id);
        if (existing.isEmpty()) {
            userRoles.deleteAll(userRoles.findByUserId(userId));
            userRoles.flush();
            userRoles.save(new UserRole(user, role));
            user.setAuthVersion(user.getAuthVersion() + 1);
            users.save(user);
        }
    }

    @Transactional
    public void revokeRole(Long userId, Long roleId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
        userRoles.deleteById(new UserRoleId(userId, roleId));
        user.setAuthVersion(user.getAuthVersion() + 1);
        users.save(user);
    }

    /** Idempotent: granting Branch access the user already has is a no-op. */
    @Transactional
    public void grantBranchAccess(Long userId, Long branchId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
        Branch branch = branches.findById(branchId).orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา"));
        UserBranchAccessId id = new UserBranchAccessId(userId, branchId);
        Optional<UserBranchAccess> existing = userBranchAccess.findById(id);
        if (existing.isEmpty()) {
            userBranchAccess.save(new UserBranchAccess(user, branch));
        }
    }

    @Transactional
    public void revokeBranchAccess(Long userId, Long branchId) {
        userBranchAccess.deleteById(new UserBranchAccessId(userId, branchId));
    }

    @Transactional
    public AppUser deactivate(Long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
        user.setUserStatus(false);
        user.setAuthVersion(user.getAuthVersion() + 1);
        return users.save(user);
    }

    @Transactional
    public AppUser activate(Long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
        Staff staff = user.getStaff();
        if (staff != null && staff.getTerminationDate() != null) {
            throw new IllegalArgumentException(
                    "ไม่สามารถเปิดบัญชีของพนักงานที่มีวันลาออกแล้ว");
        }
        user.setUserStatus(true);
        user.setAuthVersion(user.getAuthVersion() + 1);
        return users.save(user);
    }

    /** rawPassword may be null/blank to leave the password unchanged. */
    @Transactional
    public AppUser updateProfile(Long userId, String username, String email, String rawPassword, Long actorId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
        String normalized = requireText(username, "username");
        if (normalized.contains("@")) throw new IllegalArgumentException("ชื่อผู้ใช้ห้ามมี @");
        users.findByUsername(normalized)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("ชื่อผู้ใช้นี้มีอยู่แล้ว");
                });
        user.setUsername(normalized);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null) {
            users.findByEmailIgnoreCase(normalizedEmail)
                    .filter(other -> !other.getId().equals(userId))
                    .ifPresent(other -> { throw new IllegalArgumentException("อีเมลนี้มีอยู่แล้ว"); });
        }
        user.setEmail(normalizedEmail);
        if (rawPassword != null && !rawPassword.isBlank()) {
            if (rawPassword.length() < 12 || rawPassword.length() > 100) {
                throw new IllegalArgumentException("รหัสผ่านต้องมีความยาว 12-100 ตัวอักษร");
            }
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
        user.setAuthVersion(user.getAuthVersion() + 1);
        user.setUserUpdateAt(Instant.now());
        user.setUserUpdateBy(actorId);
        return users.save(user);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value.trim();
    }

    private static String normalizeEmail(String email) {
        String normalized = requireText(email, "email");
        if (normalized.length() > 254
                || !EMAIL_FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("รูปแบบอีเมลไม่ถูกต้อง");
        }
        return normalized;
    }
}
