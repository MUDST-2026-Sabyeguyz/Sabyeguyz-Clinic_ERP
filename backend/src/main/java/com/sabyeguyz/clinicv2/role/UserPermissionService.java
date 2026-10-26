package com.sabyeguyz.clinicv2.role;

import java.util.List;
import com.sabyeguyz.clinicv2.auth.AppUser;
import com.sabyeguyz.clinicv2.auth.AppUserRepository;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.auth.UserRoleRepository;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPermissionService {
    private final UserPermissionRepository overrides;
    private final AppUserRepository users;
    private final PermissionRepository permissions;
    private final UserRoleRepository roles;
    private final CurrentActor actor;

    public UserPermissionService(UserPermissionRepository overrides, AppUserRepository users,
                                 PermissionRepository permissions, UserRoleRepository roles, CurrentActor actor) {
        this.overrides = overrides;
        this.users = users;
        this.permissions = permissions;
        this.roles = roles;
        this.actor = actor;
    }

    @Transactional
    public UserPermission setAllowed(
            Long userId, Long permissionId, boolean allowed) {
        AppUser manager = requireManager();
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
        Permission permission = permissions.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสิทธิ์"));
        UserPermission override = overrides
                .findByUserIdAndPermissionId(userId, permissionId)
                .orElseGet(() -> new UserPermission(user, permission, allowed, manager, ClinicTime.now()));
        override.setAllowed(allowed);
        override.setUpdatedBy(manager);
        override.setUpdatedAt(ClinicTime.now());

        return overrides.save(override);
    }

    @Transactional(readOnly = true)
    public List<UserPermission> list(Long userId) {
        requireManager();
        if (!users.existsById(userId)) {
            throw new IllegalArgumentException("ไม่พบผู้ใช้");
        }

        return overrides.findByUserId(userId);
    }

    @Transactional
    public void reset(Long userId, Long permissionId) {
        requireManager();
        if (!users.existsById(userId)) {
            throw new IllegalArgumentException("ไม่พบผู้ใช้");
        }
        if (!permissions.existsById(permissionId)) {
            throw new IllegalArgumentException("ไม่พบสิทธิ์");
        }
        overrides.findByUserIdAndPermissionId(userId, permissionId)
                .ifPresent(overrides::delete);
    }

    private AppUser requireManager() {
        Long actorId = actor.requireId();
        boolean manager = roles.findByUserId(actorId).stream()
                .anyMatch(link -> "Manager".equalsIgnoreCase(link.getRole().getRoleName()));
        if (!manager) {
            throw new AccessDeniedException("เฉพาะ Manager เท่านั้นที่เปลี่ยนสิทธิ์ได้");
        }

        return users.findById(actorId)
                .orElseThrow(() -> new AccessDeniedException("ไม่พบผู้ใช้งาน"));
    }
}